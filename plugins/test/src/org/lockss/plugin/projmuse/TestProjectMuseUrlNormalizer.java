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

package org.lockss.plugin.projmuse;

import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.UrlNormalizer;
import org.lockss.test.*;

public class TestProjectMuseUrlNormalizer extends LockssTestCase {

  protected UrlNormalizer norm;
  
  protected MockArchivalUnit mauHttp;
  
  protected MockArchivalUnit mauHttps;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    norm = makeNormalizer();
    mauHttp = new MockArchivalUnit();
    mauHttp.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.BASE_URL.getKey(),
                                                        "http://www.example.com/"));
    mauHttps = new MockArchivalUnit();
    mauHttps.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.BASE_URL.getKey(),
                                                         "https://www.example.com/"));
  }

  public void testUrlNormalizer() throws Exception {
    // Null
    assertNull(norm.normalizeUrl(null, mauHttp));
    assertNull(norm.normalizeUrl(null, mauHttps));
    // Off-site URL stays the same
    assertEquals("http://other.example.com/favicon.ico",
                 norm.normalizeUrl("http://other.example.com/favicon.ico", mauHttp));
    assertEquals("https://other.example.com/favicon.ico",
                 norm.normalizeUrl("https://other.example.com/favicon.ico", mauHttps));
    // On-site HTTP URL stays the same
    assertEquals("http://www.example.com/favicon.ico",
                 norm.normalizeUrl("http://www.example.com/favicon.ico", mauHttp));
    assertEquals("http://www.example.com/favicon.ico",
                 norm.normalizeUrl("http://www.example.com/favicon.ico", mauHttps));
    // On-site HTTPS URL: stays the same if HTTPS base URL, normalized to HTTP if HTTP base URL
    assertEquals("http://www.example.com/favicon.ico",
                 norm.normalizeUrl("https://www.example.com/favicon.ico", mauHttp));
    assertEquals("https://www.example.com/favicon.ico",
                 norm.normalizeUrl("https://www.example.com/favicon.ico", mauHttps));
  }
  
  public UrlNormalizer makeNormalizer() {
    return new ProjectMuseUrlNormalizer();
  }
  
}
