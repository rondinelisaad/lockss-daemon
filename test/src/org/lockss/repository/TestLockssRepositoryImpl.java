/*
 * $Id: TestLockssRepositoryImpl.java,v 1.17 2003-02-13 06:28:52 claire Exp $
 */

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

package org.lockss.repository;

import java.io.*;
import java.util.*;
import java.net.MalformedURLException;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.Configuration;
import org.lockss.daemon.TestConfiguration;
import org.lockss.daemon.*;

/**
 * This is the test class for org.lockss.daemon.LockssRepositoryImpl
 */

public class TestLockssRepositoryImpl extends LockssTestCase {
  private LockssRepository repo;
  private MockArchivalUnit mau;
  private String tempDirPath;

  public TestLockssRepositoryImpl(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    configCacheLocation(tempDirPath);
    mau = new MockArchivalUnit();
    repo = (new LockssRepositoryImpl()).repositoryFactory(mau);
  }

  public void testRepositoryFactory() {
    String auId = mau.getAUId();
    LockssRepository repo1 = repo.repositoryFactory(mau);
    assertNotNull(repo1);
    mau.setAuId(auId + "test");
    LockssRepository repo2 = repo.repositoryFactory(mau);
    assertTrue(repo1 != repo2);
    mau.setAuId(auId);
    repo2 = repo.repositoryFactory(mau);
    assertEquals(repo1, repo2);
  }

  public void testFileLocation() throws Exception {
    tempDirPath += "cache/none/";
    File testFile = new File(tempDirPath);
    assertTrue(!testFile.exists());

    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    assertTrue(testFile.exists());
    tempDirPath += "www.example.com/http/";
    testFile = new File(tempDirPath);
    assertTrue(testFile.exists());
    tempDirPath += "testDir/branch1/leaf1/";
    testFile = new File(tempDirPath);
    assertTrue(testFile.exists());
  }

  public void testGetRepositoryNode() throws Exception {
    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/leaf2",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/leaf3",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf4", "test stream", null);

    RepositoryNode node =
        repo.getNode("http://www.example.com/testDir");
    assertTrue(!node.hasContent());
    assertEquals("http://www.example.com/testDir", node.getNodeUrl());
    node = repo.getNode("http://www.example.com/testDir/branch1");
    assertTrue(!node.hasContent());
    assertEquals("http://www.example.com/testDir/branch1", node.getNodeUrl());
    node =
        repo.getNode("http://www.example.com/testDir/branch2/leaf3");
    assertTrue(node.hasContent());
    assertEquals("http://www.example.com/testDir/branch2/leaf3",
                 node.getNodeUrl());
    node = repo.getNode("http://www.example.com/testDir/leaf4");
    assertTrue(node.hasContent());
    assertEquals("http://www.example.com/testDir/leaf4", node.getNodeUrl());
  }

  public void testDeleteNode() throws Exception {
    createLeaf("http://www.example.com/test1", "test stream", null);

    RepositoryNode node = repo.getNode("http://www.example.com/test1");
    assertTrue(node.hasContent());
    assertTrue(!node.isInactive());
    repo.deleteNode("http://www.example.com/test1");
    assertTrue(!node.hasContent());
    assertTrue(node.isInactive());
  }

  public void testCaching() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf2", null, null);

    LockssRepositoryImpl repoImpl = (LockssRepositoryImpl)repo;
    assertEquals(0, repoImpl.getCacheHits());
    assertEquals(2, repoImpl.getCacheMisses());
    RepositoryNode leaf =
        repo.getNode("http://www.example.com/testDir/leaf1");
    assertEquals(1, repoImpl.getCacheHits());
    RepositoryNode leaf2 =
        repo.getNode("http://www.example.com/testDir/leaf1");
    assertSame(leaf, leaf2);
    assertEquals(2, repoImpl.getCacheHits());
    assertEquals(2, repoImpl.getCacheMisses());
  }

  public void testWeakReferenceCaching() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", null, null);

    LockssRepositoryImpl repoImpl = (LockssRepositoryImpl)repo;
    RepositoryNode leaf =
        repo.getNode("http://www.example.com/testDir/leaf1");
    RepositoryNode leaf2 = null;
    int loopSize = 1;
    int refHits = 0;
    // create leafs in a loop until fetching an leaf1 creates a cache miss
    while (true) {
      loopSize *= 2;
      for (int ii=0; ii<loopSize; ii++) {
        createLeaf("http://www.example.com/testDir/testleaf"+ii, null, null);
      }
      int misses = repoImpl.getCacheMisses();
      refHits = repoImpl.getRefHits();
      leaf2 = repo.getNode("http://www.example.com/testDir/leaf1");
      if (repoImpl.getCacheMisses() == misses+1) {
        break;
      }
    }
    assertSame(leaf, leaf2);
    assertEquals(refHits+1, repoImpl.getRefHits());
  }

  public void testCusCompare() throws Exception {
    CachedUrlSetSpec spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    CachedUrlSetSpec spec2 = new RangeCachedUrlSetSpec("http://www.example.com");
    MockCachedUrlSet cus1 = new MockCachedUrlSet(spec1);
    MockCachedUrlSet cus2 = new MockCachedUrlSet(spec2);
    assertEquals(LockssRepository.BELOW, repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test/subdir");
    cus1 = new MockCachedUrlSet(spec1);
    cus2 = new MockCachedUrlSet(spec2);
    assertEquals(LockssRepository.ABOVE, repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test/");
    cus1 = new MockCachedUrlSet(spec1);
    cus2 = new MockCachedUrlSet(spec2);
    Vector v = new Vector(2);
    v.addElement("test 1");
    v.addElement("test 2");
    Vector v2 = new Vector(1);
    v2.addElement("test 3");
    cus1.setFlatItSource(v);
    cus2.setFlatIterator(v2.iterator());
    assertEquals(LockssRepository.SAME_LEVEL_NO_OVERLAP, repo.cusCompare(cus1, cus2));

    v2.addElement("test 2");
    cus2.setFlatIterator(v2.iterator());
    assertEquals(LockssRepository.SAME_LEVEL_OVERLAP, repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test/subdir2");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/subdir");
    cus1 = new MockCachedUrlSet(spec1);
    cus2 = new MockCachedUrlSet(spec2);
    assertEquals(LockssRepository.NO_RELATION, repo.cusCompare(cus1, cus2));
  }

  public static void configCacheLocation(String location)
    throws IOException {
    String s = LockssRepositoryImpl.PARAM_CACHE_LOCATION + "=" + location;
    TestConfiguration.setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(s)));
  }

  private RepositoryNode createLeaf(String url, String content,
                                    Properties props) throws Exception {
    return TestRepositoryNodeImpl.createLeaf(repo, url, content, props);
  }

}
