/*
 * $Id$
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire.oup;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.highwire.HighWireDrupalHtmlFilterFactory;
import org.lockss.util.Logger;

public class OUPHtmlHashFilterFactory extends HighWireDrupalHtmlFilterFactory {
  
  private static final Logger log = Logger.getLogger(OUPHtmlHashFilterFactory.class);
  
  protected static NodeFilter[] filters = new NodeFilter[] {
//    HtmlNodeFilters.allExceptSubtree(
//        HtmlNodeFilters.tagWithAttribute("section", "id", "section-content"),
//        HtmlNodeFilters.tagWithAttribute("div", "class", "highwire-markup")),
    // the remaining fields will not need to be filtered
    // right sidebar 
    HtmlNodeFilters.tagWithAttributeRegex("div", "class", "sidebar-right-wrapper"),
    // content-header from QJM
    HtmlNodeFilters.tagWithAttributeRegex("div", "class", "content-header"),
    // do not hash citing and related section, nor keywords, by author, and eletters sections
    HtmlNodeFilters.tagWithAttributeRegex("div", "class", "(citing|related)-articles?"),
    HtmlNodeFilters.tagWithAttributeRegex("div", "class", "-(keywords|by-author|eletters|challenge)"),
    HtmlNodeFilters.tagWithAttribute("div", "class", "panel-separator"),
    HtmlNodeFilters.tagWithText("h3", "related data", true),
    // tab text changes -- Information (& metrics)?
    HtmlNodeFilters.tagWithAttributeRegex("ul", "class", "-ajax-tab"),
    // links appeared/disappeared http://nutritionreviews.oxfordjournals.org/content/67/4/222
    HtmlNodeFilters.tagWithAttributeRegex("ul", "class", "highwire-figure-links"),
    HtmlNodeFilters.tagWithAttributeRegex("div", "class", "highwire.+variants?-list"),
    // OUP author section kept changing formating and spacing
    HtmlNodeFilters.allExceptSubtree(
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "highwire-article-citation"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "highwire-cite-title")),
  };
  
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    
    return super.createFilteredInputStream(au, in, encoding, filters);
  }
  
  @Override
  public boolean doWSFiltering() {
    return true;
  }
  @Override
  public boolean doTagAttributeFiltering() {
    return false;
  }
  @Override
  public boolean doXformToText() {
    return true;
  }
}
