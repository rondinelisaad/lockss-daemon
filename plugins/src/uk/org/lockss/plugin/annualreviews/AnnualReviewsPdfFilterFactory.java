/*
 * $Id: AnnualReviewsPdfFilterFactory.java,v 1.4 2013-06-12 02:34:30 thib_gc Exp $
 */

/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package uk.org.lockss.plugin.annualreviews;

import java.util.List;
import java.util.regex.Pattern;

import org.lockss.filter.pdf.SimplePdfFilterFactory;
import org.lockss.pdf.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

public class AnnualReviewsPdfFilterFactory extends SimplePdfFilterFactory {

  /**
   * <p>A logger for use by this class.</p>
   */
  private static final Logger logger = Logger.getLogger(AnnualReviewsPdfFilterFactory.class);

  protected static class DownloadedFromWorker extends PdfTokenStreamWorker {

    public DownloadedFromWorker() {
      super(Direction.BACKWARD);
    }
    
    // FIXME 1.62
    protected static final Pattern DOWNLOADED_FROM_PATTERN =
        Pattern.compile(".*Downloaded from (?:http://)?[-0-9A-Za-z]+(?:\\.[-0-9A-Za-z]+)+");
    
    private int state;
    
    private int beginIndex;
    
    private int endIndex;
    
    private boolean result;
    
    @Override
    public void setUp() throws PdfException {
      super.setUp();
      this.result = false;
      this.state = 0;
      this.beginIndex = -1;
      this.endIndex = -1;
    }
    
    @Override
    public void operatorCallback() throws PdfException {
      if (logger.isDebug3()) {
        logger.debug3("DownloadedFromWorker: initial: " + state);
        logger.debug3("DownloadedFromWorker: index: " + getIndex());
        logger.debug3("DownloadedFromWorker: operator: " + getOpcode());
      }
      
      switch (state) {
        
        case 0: {
          if (getIndex() == getTokens().size() - 1 && isEndTextObject()) {
            endIndex = getIndex();
            ++state;
          }
          else {
            stop();
          }
        } break;
        
        case 1: {
          // FIXME 1.62
          if (isShowTextMatches(DOWNLOADED_FROM_PATTERN)) {
            ++state;
          }
          else if (isBeginTextObject()) {
            stop();
          }
        } break;
        
        case 2: {
          // FIXME 1.62
          if ("Tm".equals(getOpcode())) {
            ++state;
          }
          else if (isBeginTextObject()) {
            stop();
          }
        } break;
        
        case 3: {
          if (getIndex() == 0 && isBeginTextObject()) {
            beginIndex = getIndex();
            result = true;
            stop();
          }
        } break;

        default: {
          throw new PdfException("Invalid state in DownloadedFromWorker: " + state);
        }
    
      }
      
      if (logger.isDebug3()) {
        logger.debug3("DownloadedFromWorker: final: " + state);
        logger.debug3("DownloadedFromWorker: result: " + result);
      }
      
    }
    
  }
  
  protected static class ForPersonalUseWorker extends PdfTokenStreamWorker {

    public ForPersonalUseWorker() {
      super(Direction.BACKWARD);
    }
    
    // FIXME 1.62
    protected static final Pattern FOR_PERSONAL_USE_PATTERN =
        Pattern.compile("by .* on [0-9]{2}/[0-9]{2}/[0-9]{2}. For personal use only.");
    
    private int state;
    
    private int beginIndex;
    
    private int endIndex;
    
    private boolean result;
    
    @Override
    public void setUp() throws PdfException {
      super.setUp();
      this.result = false;
      this.state = 0;
      this.beginIndex = -1;
      this.endIndex = -1;
    }
    
    @Override
    public void operatorCallback() throws PdfException {
      if (logger.isDebug3()) {
        logger.debug3("ForPersonalUseWorker: initial: " + state);
        logger.debug3("ForPersonalUseWorker: index: " + getIndex());
        logger.debug3("ForPersonalUseWorker: operator: " + getOpcode());
      }
      
      switch (state) {
        
        case 0: {
          if (getIndex() == getTokens().size() - 1 && isEndTextObject()) {
            endIndex = getIndex();
            ++state;
          }
        } break;
        
        case 1: {
          // FIXME 1.62
          if (isShowTextMatches(FOR_PERSONAL_USE_PATTERN)) {
            ++state;
          }
          else if (isBeginTextObject()) {
            stop();
          }
        } break;
        
        case 2: {
          // FIXME 1.62
          if ("Tm".equals(getOpcode())) {
            ++state;
          }
          else if (isBeginTextObject()) {
            stop();
          }
        } break;
        
        case 3: {
          if (getIndex() == 0 && isBeginTextObject()) {
            beginIndex = getIndex();
            result = true;
            stop();
          }
        } break;

        default: {
          throw new PdfException("Invalid state in ForPersonalUseWorker: " + state);
        }
    
      }
      
      if (logger.isDebug3()) {
        logger.debug3("ForPersonalUseWorker: final: " + state);
        logger.debug3("ForPersonalUseWorker: result: " + result);
      }
      
    }
    
  }
  
  @Override
  public void transform(ArchivalUnit au, PdfDocument pdfDocument) throws PdfException {
    pdfDocument.unsetCreationDate();
    pdfDocument.unsetModificationDate();
    PdfUtil.normalizeTrailerId(pdfDocument);
    DownloadedFromWorker downloadedFromWorker = new DownloadedFromWorker();
    ForPersonalUseWorker forPersonalUseWorker = new ForPersonalUseWorker();
    boolean firstPage = true;
    for (PdfPage pdfPage : pdfDocument.getPages()) {
      boolean hasDoneSomething = false;
      for (PdfTokenStream pdfTokenStream : pdfPage.getAllTokenStreams()) {
        downloadedFromWorker.process(pdfTokenStream);
        if (downloadedFromWorker.result) {
          hasDoneSomething = true;
          List<PdfToken> tokens = pdfTokenStream.getTokens();
          tokens.subList(downloadedFromWorker.beginIndex, downloadedFromWorker.endIndex).clear();
          pdfTokenStream.setTokens(tokens);
        }
        forPersonalUseWorker.process(pdfTokenStream);
        if (forPersonalUseWorker.result) {
          hasDoneSomething = true;
          List<PdfToken> tokens = pdfTokenStream.getTokens();
          tokens.subList(forPersonalUseWorker.beginIndex, forPersonalUseWorker.endIndex).clear();
          pdfTokenStream.setTokens(tokens);
        }
      }
      if (firstPage && !hasDoneSomething) {
        return; // no need to try the other pages
      }
      else {
        firstPage = false;
      }
    }
  }
  
}
