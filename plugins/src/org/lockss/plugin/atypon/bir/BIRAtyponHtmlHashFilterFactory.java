/*
 * $Id$
 */

/*

Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon.bir;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.filter.html.HtmlNodeFilters;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;
import org.lockss.util.Logger;

public class BIRAtyponHtmlHashFilterFactory 
  extends BaseAtyponHtmlHashFilterFactory {

  private static final Logger log = Logger.getLogger(BIRAtyponHtmlHashFilterFactory.class);

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding) {
    NodeFilter[] filters = new NodeFilter[] {
        // handled by parent: script, sfxlink, stylesheet
        // literatumArticleToolsWidget
        
        // toc - BJR logo image right below pageHeader
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", 
                                              "^widget general-image"),
        // toc, abs, full, ref - menu above breadcrumbs
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "menuXml"),
        // toc - free.gif image tied to an abs
        HtmlNodeFilters.tagWithAttributeRegex("img", "src", "free.gif"),
        // toc - pulldown with sections - may add citedby later
        HtmlNodeFilters.tagWithAttribute("div", "class", 
                                         "publicationTooldropdownContainer"),
        // toc, abs - share social media
        HtmlNodeFilters.tagWithAttributeRegex("div", "class",
                                              "general-bookmark-share"),
        // toc - right column impact factor block - no unique name found
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", 
            "widget\\s+layout-one-column\\s+none\\s+widget-regular\\s+widget-border-toggle"),
        // ref - this seems unused but may get turned on
        // http://www.birpublications.org/doi/ref/10.1259/bjr.20130571
        HtmlNodeFilters.tagWithAttribute("div",  "id", "MathJax_Message"),
        // on full text and references page the ways to linkout to the reference get 
        // added to (GoogleScholar, Medline, ISI, abstract, etc) 
        // leave the content (NLM_article-title, NLM_year, etc), 
        // but remove everything else (links and punctuation between options) 
        HtmlNodeFilters.allExceptSubtree(
            HtmlNodeFilters.tagWithAttribute("table", "class", "references"),
            HtmlNodeFilters.tagWithAttributeRegex("span", "class", "NLM_")),

    };
    // super.createFilteredInputStream adds bir filter to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    return super.createFilteredInputStream(au, in, encoding, filters);
  }
  
  @Override
  public boolean doTagRemovalFiltering() {
    return true;
  }
  
  @Override
  public boolean doWSFiltering() {
    return true;
  }

}

