/*
 * $Id$
 */

/*

 Copyright (c) 2000-2016 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss.iospress;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.JatsPublishingSchemaHelper;
import org.lockss.plugin.clockss.Onix3BooksSchemaHelper;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;


public class IosPressXmlMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  private static final Logger log = Logger.getLogger(IosPressXmlMetadataExtractorFactory.class);

  private static SourceXmlSchemaHelper Onix3Helper = null;
  private static SourceXmlSchemaHelper JatsHelper = null;

  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
    return new IosPressXmlMetadataExtractor();
  }

  public class IosPressXmlMetadataExtractor extends SourceXmlMetadataExtractor {
    private static final String BOOKPATH = "iosbooks-released";

    @Override
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
      // Once you have it, just keep returning the same one. It won't change.
      if ((cu.getUrl()).contains(BOOKPATH)) {
        if (Onix3Helper == null) {
          Onix3Helper = new Onix3BooksSchemaHelper();
        }
        return Onix3Helper;
      } else { 
        if (JatsHelper == null) {
          JatsHelper = new JatsPublishingSchemaHelper();
        }
        return JatsHelper;
      }
    }


    /* In this case, use the RecordReference + .pdf for the matching file */
    @Override
    protected List<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, CachedUrl cu,
        ArticleMetadata oneAM) {

      String url_string = cu.getUrl();
      List<String> returnList = new ArrayList<String>();
      if (helper == Onix3Helper) {
        String filenameValue = oneAM.getRaw(Onix3BooksSchemaHelper.ONIX_RR);
        String cuBase = FilenameUtils.getFullPath(url_string);
        String fullPathFile = cuBase + filenameValue + ".pdf";
        log.debug3("pdfName is " + fullPathFile);
        returnList.add(fullPathFile);
      } else {
        // filename is just the same a the XML filename but with .pdf 
        // instead of .xml
        String pdfName = url_string.substring(0,url_string.length() - 3) + "pdf";
        log.debug3("pdfName is " + pdfName);
        returnList.add(pdfName);
      }
      return returnList;        
    }

    @Override
    protected void postCookProcess(SourceXmlSchemaHelper schemaHelper, 
        CachedUrl cu, ArticleMetadata thisAM) {

      log.debug3("in iospress postCookProcess");
      if (schemaHelper == JatsHelper) {
        //If we didn't get a valid date value, use the copyright year if it's there
        if (thisAM.get(MetadataField.FIELD_DATE) == null) {
          if (thisAM.getRaw(JatsPublishingSchemaHelper.JATS_date) != null) {
            thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_date));
          } else {// last chance
            thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_edate));
          }
        }
      }

    }    
    
  }
}
