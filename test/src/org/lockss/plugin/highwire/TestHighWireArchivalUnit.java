/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.File;
import java.net.*;
import java.util.*;
import gnu.regexp.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.state.*;
import org.lockss.test.*;
import org.lockss.plugin.*;
import org.lockss.repository.TestLockssRepositoryServiceImpl;

public class TestHighWireArchivalUnit extends LockssTestCase {
  public static final long WEEK_MS = 1000 * 60 * 60 * 24 * 7;
  private MockLockssDaemon theDaemon = new MockLockssDaemon();
  private MockArchivalUnit mau;

  public TestHighWireArchivalUnit(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    TestLockssRepositoryServiceImpl.configCacheLocation(tempDirPath);
  }

  public void testGetVolumeNum() {

  }

  private HighWireArchivalUnit makeAU(URL url, int volume)
      throws ArchivalUnit.ConfigurationException {
    Properties props = new Properties();
    props.setProperty(HighWirePlugin.VOL_PROP, Integer.toString(volume));
    if (url != null) {
      props.setProperty(HighWirePlugin.BASE_URL_PROP, url.toString());
    }
    Configuration config = ConfigurationUtil.fromProps(props);
    return new HighWireArchivalUnit(new HighWirePlugin(), config);
  }

  public void testConstructNullUrl() throws Exception {
    try {
      makeAU(null, 1);
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }
  }

  public void testConstructNegativeVolume() throws Exception {
    URL url = new URL("http://www.example.com/");
    try {
      makeAU(url, -1);
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }
  }


  public void testShouldCacheRootPage() throws Exception {
    URL base = new URL("http://shadow1.stanford.edu/");
    int volume = 322;
    ArchivalUnit hwAu = makeAU(base, volume);
    theDaemon.getLockssRepository(hwAu);
    CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(base.toString());
    GenericFileCachedUrlSet cus = new GenericFileCachedUrlSet(hwAu, spec);
    UrlCacher uc =
      cus.makeUrlCacher("http://shadow1.stanford.edu/lockss-volume322.shtml");
    assertTrue(uc.shouldBeCached());
  }

  public void testShouldNotCachePageFromOtherSite() throws Exception {
    URL base = new URL("http://shadow1.stanford.edu/");
    int volume = 322;
    ArchivalUnit hwAu = makeAU(base, volume);
    CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(base.toString());
    GenericFileCachedUrlSet cus = new GenericFileCachedUrlSet(hwAu, spec);
    UrlCacher uc =
      cus.makeUrlCacher("http://shadow2.stanford.edu/lockss-volume322.shtml");
    assertTrue(!uc.shouldBeCached());
  }

  public void testPathInUrlThrowsException() throws Exception {
    URL url = new URL("http://www.example.com/path");
    try {
      makeAU(url, 10);
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch(ArchivalUnit.ConfigurationException e) {
    }
  }

  public void testShouldDoNewContentCrawlTooEarly() throws Exception {
    ArchivalUnit hwAu =
      makeAU(new URL("http://shadow1.stanford.edu/"), 322);

    AuState aus = new MockAuState(null, TimeBase.nowMs(), -1, -1);

    assertTrue(!hwAu.shouldCrawlForNewContent(aus));
  }

  public void testShouldDoNewContentCrawlFor0() throws Exception {
    ArchivalUnit hwAu =
      makeAU(new URL("http://shadow1.stanford.edu/"), 322);

    AuState aus = new MockAuState(null, 0, -1, -1);

    assertTrue(hwAu.shouldCrawlForNewContent(aus));
  }

  public void testShouldDoNewContentCrawlEachMonth() throws Exception {
    ArchivalUnit hwAu =
      makeAU(new URL("http://shadow1.stanford.edu/"), 322);

    AuState aus = new MockAuState(null, 4 * WEEK_MS, -1, -1);

    assertTrue(hwAu.shouldCrawlForNewContent(aus));
  }
}
