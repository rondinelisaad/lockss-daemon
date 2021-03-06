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

package org.lockss.plugin.clockss.iop;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.Onix2BooksSchemaHelper;
import org.lockss.plugin.clockss.Onix3BooksSchemaHelper;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class IopOnixXmlMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  private static final Logger log = Logger.getLogger(IopOnixXmlMetadataExtractorFactory.class);

  private static SourceXmlSchemaHelper Onix2Helper = null;
  private static SourceXmlSchemaHelper Onix3Helper = null;
 
  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
    return new IopOnixXmlMetadataExtractor();
  }

  public static class IopOnixXmlMetadataExtractor extends SourceXmlMetadataExtractor {

    @Override
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
      throw new ShouldNotHappenException("This version of the schema setup cannot be used for this plugin");
    }
    
    
    /*
     * IOP Books use both Onix2 and Onix3, both short and long form. 
     * Thelong/short isn't an issue because the SchemaHelper handles both
     * The 2/3 is harder because the difference isn't at a top node, so hard to differentiate
     * just from the xml paths and the DTD definition isn't used consistently.
     * Try - 
     * DTD
     * <!DOCTYPE ONIXmessage SYSTEM "http://www.editeur.org/onix/2.1/short/onix-international.dtd">
     * or
     * "release=" off the top node
     * /ONIXmessage/@release=3.0
     * or
     * look for a particular titlegroup layout
     * 
     */
    @Override
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu, Document doc) {
      // Once you have it, just keep returning the same one. It won't change.
      String system = null;
      String release = null;
      
      if (doc == null) return null;
      DocumentType doctype = doc.getDoctype();
      if (doctype != null) {
        system  = doctype.getSystemId();
        log.debug3("DOCTYPE URI: " + doctype.getSystemId());
      }
      Element root = doc.getDocumentElement();
      if (root != null) {
        release = root.getAttribute("release");
        if (release != null) {
          log.debug3("releaseNum : " + release);
        }
      }
      if( ((system != null) && system.contains("/onix/2")) ||
           (( release != null) && release.startsWith("2")) ) {
        if (Onix2Helper == null) {
          Onix2Helper = new Onix2BooksSchemaHelper();
        }
        return Onix2Helper;
      } else if ( ((system != null) && system.contains("/onix/3")) ||
        (( release != null) && release.startsWith("3")) ) {
        if (Onix3Helper == null) {
          Onix3Helper = new Onix3BooksSchemaHelper();
        }
        return Onix3Helper;
      } else {
        log.warning("guessing at XML schema - using ONIX2");
      }
      if (Onix2Helper == null) {
        Onix2Helper = new Onix2BooksSchemaHelper();
      }
      return Onix2Helper;
    }


    /* 
     * This is a little horrible, but making it work. 
     * 
     * In this case, build up the filename the ISBN13 and ".pdf"
     * but occasionally that doesn't find it, so the next fallback is to try
     * the second half of the doi
     * what makes this particularly tricky is that the publisher provides the
     * PDF filename using the ISBN *with* the hyphens which is a tricky 
     * proposition made trickier by their occasionally incorrect hyphen placement. 
     * So we are going to brute force this by trying the three likely 
     * combinations
     * We will also ask them to begin providing PDF without hyphens in the future
     * (If they do not, we may want to consider a keeping a static list of the
     * files around with the hyphens stripped out available to each XML file
     * during an extraction)
     *
     * In most cases, the isbn13 looks like this
     * 9780750311441 (no hyphen) 

     * In most cases the doi value is like this
     * 10.1088/978-0-7503-1103-8
     * But in some cases it looks like this:
     * http://dx.doi.org/10.1088/978-0-750-31173-1
     * in a few cases it looks like this
     * http://dx.doi.org/10.1088/9780750312363  (no hyphens)
     * but in all cases it has at least one slash

     */
    @Override
    protected List<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, CachedUrl cu,
        ArticleMetadata oneAM) {

      
      String isbn13 = oneAM.getRaw(helper.getFilenameXPathKey());
      String fullDOI = (helper == Onix2Helper) ? oneAM.getRaw(Onix2BooksSchemaHelper.ONIX_idtype_doi) : 
        oneAM.getRaw(Onix3BooksSchemaHelper.ONIX_idtype_doi);
      String doi_isbn13 = null;
      String clean_doi = null;
      if (fullDOI != null) {
        doi_isbn13 = fullDOI.substring(fullDOI.lastIndexOf("/") + 1);
      }
      
      String cuBase = FilenameUtils.getFullPath(cu.getUrl());
      List<String> returnList = new ArrayList<String>();
      // need to add in the slashes in various locations... these are
      // guesses specific to what IOP seems to be delivering which isn't always correct
      if (isbn13 != null && isbn13.length() == 13) {
        // try 3-1-4-4-1 and 3-1-3-5-1 of the isbn13 value
        String startval = isbn13.substring(0,3) + "-" + isbn13.substring(3,4) + "-";
        String endval = "-" + isbn13.substring(12,13);
        String midvals1 =  isbn13.substring(4,8) + "-" + isbn13.substring(8,12);
        String midvals2 =  isbn13.substring(4,7) + "-" + isbn13.substring(7,12);
        returnList.add(cuBase + startval + midvals1 + endval + ".pdf");
        returnList.add(cuBase + startval + midvals2 + endval + ".pdf");
      }
      if (doi_isbn13 != null) {
        // and if the doi value has hyphens, just try what they gave us
        if  (doi_isbn13.contains("-")) { 
          returnList.add(cuBase + doi_isbn13 + ".pdf");
        } else if (doi_isbn13.length() == 13){
          // crud - a few cases where the doi didn't have hyphens...do it manually
          // try 3-1-4-4-1 and 3-1-3-5-1 of the  value
          String startval = doi_isbn13.substring(0,3) + "-" + doi_isbn13.substring(3,4) + "-";
          String endval = "-" + doi_isbn13.substring(12,13);
          String midvals1 =  doi_isbn13.substring(4,8) + "-" + doi_isbn13.substring(8,12);
          String midvals2 =  doi_isbn13.substring(4,7) + "-" + doi_isbn13.substring(7,12);
          returnList.add(cuBase + startval + midvals1 + endval + ".pdf");
          returnList.add(cuBase + startval + midvals2 + endval + ".pdf");
        }
      }
      // NOTE - what we haven't tried here is a differently spaced variant on 
      // the hyphen version from the doi.
      // and for future, use just the hyphenless value of each
      returnList.add(cuBase + isbn13 + ".pdf");
      if (doi_isbn13 != null) { 
        String doi_isbn13_nohyphen = doi_isbn13.replace("-", "");
        returnList.add(cuBase + doi_isbn13_nohyphen + ".pdf");
      }
      //log.debug3("RETURNLIST: " + returnList.toString());
      return returnList;
    }

    /*
     * * In most cases the doi value is like this
    * 10.1088/978-0-7503-1103-8
    * But in some cases it looks like this:
    * http://dx.doi.org/10.1088/978-0-750-31173-1
    * This isn't valid - so now clean up and put in the clean value
    * Could have done this during filename processing but prefer to leave
    * the original raw value untouched
    */

    @Override
    protected void postCookProcess(SourceXmlSchemaHelper helper, 
        CachedUrl cu, ArticleMetadata oneAM) {
      
      String fullDOI = (helper == Onix2Helper) ? oneAM.getRaw(Onix2BooksSchemaHelper.ONIX_idtype_doi) : 
        oneAM.getRaw(Onix3BooksSchemaHelper.ONIX_idtype_doi);
      String clean_doi = null;
      if (fullDOI != null && fullDOI.contains("dx.doi.org")) {
        clean_doi = StringUtils.substringAfter(fullDOI,"http://dx.doi.org/");
        if (!(fullDOI.equals(clean_doi))) {
            oneAM.putIfBetter(MetadataField.FIELD_DOI,clean_doi);
        }
      }      
    }    

    
  }
}
