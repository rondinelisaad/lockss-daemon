/*
 * $Id$
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

package org.lockss.plugin.heterocycles;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

/*
 * Maximal hash filtering
 * html pages reviewed for filtering:
 * start url - http://www.heterocycles.jp/clockss/manifest/vol/87
 * issue     - http://www.heterocycles.jp/clockss/libraries/journal/87/9
 * full text - http://www.heterocycles.jp/clockss/libraries/fulltext/22950/87/9
 */
public class HeterocyclesHtmlHashFilterFactory implements FilterFactory {
  
  private static Logger log = 
      Logger.getLogger(HeterocyclesHtmlHashFilterFactory.class);

  public InputStream createFilteredInputStream(ArchivalUnit au, 
                                          InputStream in, String encoding) {

    NodeFilter[] filters = new NodeFilter[] {
        new TagNameFilter("script"),
        // filter out comments
        HtmlNodeFilters.commentWithRegex(".*"),
        // header
        HtmlNodeFilters.tagWithAttribute("div", "id", "header"),
        // footer
        HtmlNodeFilters.tagWithAttribute("div", "id", "footer"),
     };

    return new HtmlFilterInputStream(in, encoding, 
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }
    
}
