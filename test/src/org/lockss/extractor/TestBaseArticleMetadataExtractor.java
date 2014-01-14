/*
 * $Id: TestBaseArticleMetadataExtractor.java,v 1.13 2014-01-14 08:55:26 tlipkis Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.extractor;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.ObjectUtils;
import org.lockss.test.*;
import org.lockss.daemon.*;
import org.lockss.config.*;
import org.lockss.plugin.*;
import org.lockss.util.*;

public class TestBaseArticleMetadataExtractor extends LockssTestCase {
  static Logger log = Logger.getLogger("TestBaseArticleMetadataExtractor");

  private MockPlugin mplug;

  MockArchivalUnit mau1, mau1nopub, mau2, mau3, mau4;
  TdbAu tdbAu1, tdbAu2, tdbAu3, tdbAu4;
  Map<String,String> tdbmap1, tdbmap2, tdbmap3, tdbmap4;

  public void setUp() throws Exception {
    super.setUp();

    mplug = new MockPlugin();
    mplug.setAuConfigDescrs(ListUtil.list(ConfigParamDescr.BASE_URL,
					  ConfigParamDescr.VOLUME_NUMBER));
    Tdb tdb = new Tdb();

    // Tdb with values for some metadata fields
    Properties tdbProps = new Properties();
    tdbProps.setProperty("title", "Air and Space Volume 1");
    tdbProps.setProperty("journalTitle", "Air and Space");
    tdbProps.setProperty("attributes.isbn", "976-1-58562-317-7");
    tdbProps.setProperty("issn", "0740-2783");
    tdbProps.setProperty("eissn", "0740-2783");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.0135/12345678]");
    tdbProps.setProperty("plugin", mplug.getClass().toString());

    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title0.org/");
    tdbProps.setProperty("param.2.key", "volume");
    tdbProps.setProperty("param.2.value", "vol1");
    tdbAu1 = tdb.addTdbAuFromProperties(tdbProps);
    mau1 = makeAu(tdbAu1);

    tdbProps.remove("attributes.publisher");
    tdbProps.setProperty("title", "Air and Space Volume 1a");
    tdbProps.setProperty("param.2.value", "vol1a");
    mau1nopub = makeAu(tdb.addTdbAuFromProperties(tdbProps));

    tdbmap1 =
      MapUtil.map(MetadataField.KEY_ISSN, "0740-2783",
		  MetadataField.KEY_EISSN, "0740-2783",
		  MetadataField.KEY_ISBN, "976-1-58562-317-7",
		  MetadataField.KEY_VOLUME, "vol1",
		  MetadataField.KEY_PUBLICATION_TITLE, "Air and Space Volume 1",
                  MetadataField.KEY_SERIES_TITLE, "Air and Space",
		  MetadataField.KEY_PUBLISHER, "Publisher[10.0135/12345678]",
		  MetadataField.KEY_ACCESS_URL, "fullurl",
		  MetadataField.KEY_PUBLICATION_TYPE, "bookSeries");


    // Tdb with values for all metadata fields
    tdbProps = new Properties();
    tdbProps.setProperty("title", "Air and Space Volume 2");
    tdbProps.setProperty("journalTitle", "Air and Space");
    tdbProps.setProperty("attributes.isbn", "976-1-58562-317-7");
    tdbProps.setProperty("attributes.eisbn", "976-1-58563-317-7");
    tdbProps.setProperty("issn", "0740-2783");
    tdbProps.setProperty("eissn", "0740-2783");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.0135/12345678]");
    tdbProps.setProperty("attributes.year", "1943");
    tdbProps.setProperty("attributes.issue", "40");
    tdbProps.setProperty("plugin", mplug.getClass().toString());

    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title0.org/");
    tdbProps.setProperty("param.2.key", "volume");
    tdbProps.setProperty("param.2.value", "vol2");
    tdbAu2 = tdb.addTdbAuFromProperties(tdbProps);
    mau2 = makeAu(tdbAu2);

    tdbmap2 =
      MapUtil.map(MetadataField.KEY_EISSN, "0740-2783",
		  MetadataField.KEY_ISSN, "0740-2783",
		  MetadataField.KEY_ISBN, "976-1-58562-317-7",
		  MetadataField.KEY_EISBN, "976-1-58563-317-7",
		  MetadataField.KEY_ISSUE, "40",
		  MetadataField.KEY_VOLUME, "vol2",
		  MetadataField.KEY_PUBLICATION_TITLE, "Air and Space Volume 2",
                  MetadataField.KEY_SERIES_TITLE, "Air and Space",
		  MetadataField.KEY_DATE, "1943",
		  MetadataField.KEY_PUBLISHER, "Publisher[10.0135/12345678]",
		  MetadataField.KEY_ACCESS_URL, "fullurl",
		  MetadataField.KEY_PUBLICATION_TYPE, "bookSeries");
  }

  public void testAccessors() {
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    assertTrue(me.isAddTdbDefaults());
    me.setAddTdbDefaults(false);
    assertFalse(me.isAddTdbDefaults());
    me.setAddTdbDefaults(true);
    assertTrue(me.isAddTdbDefaults());
  }

  public void testGetCuToExtract() {
    MockCachedUrl mcu1 = new MockCachedUrl("u1");
    MockCachedUrl mcu2 = new MockCachedUrl("u2");
    ArticleFiles af = new ArticleFiles();
    af.setFullTextCu(mcu1);
    af.setRoleCu("temaki", mcu2);
    assertSame(mcu1, new BaseArticleMetadataExtractor().getCuToExtract(af));
    assertSame(mcu1, new BaseArticleMetadataExtractor(null).getCuToExtract(af));
    assertSame(mcu2,
	       new BaseArticleMetadataExtractor("temaki").getCuToExtract(af));
    assertSame(null,
	       new BaseArticleMetadataExtractor("other").getCuToExtract(af));
  }


  MockArchivalUnit makeAu(TdbAu tau) {
    MockArchivalUnit mau = new MockArchivalUnit(mplug);
    mau.setTitleConfig(new TitleConfig(tau, mplug));
    return mau;
  }

  ArticleFiles makeAf(MockArchivalUnit mau, Map amMap) {
    MockCachedUrl cu1 = new MockCachedUrl("fullurl", mau);

    cu1.setFileMetadataExtractor(new MyFileMetadataExtractor(amMap));
    
    ArticleFiles af = new ArticleFiles();
    af.setFullTextCu(cu1);
    return af;
  }

  // setAddTdbDefaults(false) shouldn't add anything to am
  public void testNoTdbDefaults() throws Exception {
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(false);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau1, map));
    assertEquals(stringMap(map), getMap(mdlist.get(0)));
  }


  // setAddTdbDefaults(true) but no tdb should add just access.url
  public void testNoTdb() throws Exception {
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16");

    List<ArticleMetadata> mdlist =
      mle.extract(MetadataTarget.Any(), makeAf(new MockArchivalUnit(), map));
    assertEquals(MapUtil.map("volume", "vol16", "access.url", "fullurl"),
		 getMap(mdlist.get(0)));
  }


  public void testTdb() throws Exception {
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16",
			  // Invalid value
			  MetadataField.FIELD_ISSN, "not_an_issn");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau1, map));
    // Copy of md values corresponding to tdb
    Map<String,String> exp = new HashMap<String,String>(tdbmap1);
    // Add/replace explicit md values 
    exp.put("volume", "vol16");
    Map<String,String> actual = getMap(mdlist.get(0));
    assertEquals(exp, actual);
  }

  public void testPreferTdbPubTrue() throws Exception {
    ConfigurationUtil.addFromArgs(BaseArticleMetadataExtractor.PARAM_PREFER_TDB_PUBLISHER,
				  "true");
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16",
			  MetadataField.FIELD_PUBLISHER, "extracted pub");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau1, map));
    // Copy of md values corresponding to tdb
    Map<String,String> exp = new HashMap<String,String>(tdbmap1);
    // Add/replace explicit md values 
    exp.put("volume", "vol16");
    Map<String,String> actual = getMap(mdlist.get(0));
    assertEquals(exp, actual);
  }

  public void testPreferTdbPubFalse() throws Exception {
    ConfigurationUtil.addFromArgs(BaseArticleMetadataExtractor.PARAM_PREFER_TDB_PUBLISHER,
				  "false");
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16",
			  MetadataField.FIELD_PUBLISHER, "extracted pub");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau1, map));
    // Copy of md values corresponding to tdb
    Map<String,String> exp = new HashMap<String,String>(tdbmap1);
    // Add/replace explicit md values 
    exp.put("volume", "vol16");
    exp.put("publisher", "extracted pub");
    Map<String,String> actual = getMap(mdlist.get(0));
    assertEquals(exp, actual);
  }

  // Unknown publisher doesn't get picked up from tdb even if
  // preferTdbPublisher=true
  public void testPreferTdbPubTrueNull() throws Exception {
    ConfigurationUtil.addFromArgs(BaseArticleMetadataExtractor.PARAM_PREFER_TDB_PUBLISHER,
				  "true");
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol16",
			  MetadataField.FIELD_PUBLISHER, "extracted pub");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau1nopub, map));
    // Copy of md values corresponding to tdb
    Map<String,String> exp = new HashMap<String,String>(tdbmap1);
    // Add/replace explicit md values 
    exp.put("publication.title", "Air and Space Volume 1a");
    exp.put("volume", "vol16");
    exp.put("publisher", "extracted pub");
    Map<String,String> actual = getMap(mdlist.get(0));
    assertEquals(exp, actual);
  }

  public void testTdbComplete() throws Exception {
    BaseArticleMetadataExtractor me = new BaseArticleMetadataExtractor();
    me.setAddTdbDefaults(true);
    ArticleMetadataListExtractor mle = new ArticleMetadataListExtractor(me);
    Map map = MapUtil.map(MetadataField.FIELD_VOLUME, "vol666");

    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(),
					       makeAf(mau2, map));
    // Copy of md values corresponding to tdb
    Map<String,String> exp = new HashMap<String,String>(tdbmap2);
    // Add/replace explicit md values 
    exp.putAll(stringMap(map));
    Map<String,String> actual = getMap(mdlist.get(0));
    assertEquals(exp, actual);
  }

  Map<String,String> getMap(final ArticleMetadata am) {
    return new HashMap<String,String>() {{
	for (String field : am.keySet()) {
	  put(field, am.get(field));
	}
      }};
  }
      
  Map<String,String> stringMap(final Map<MetadataField,String> map) {
    return new HashMap<String,String>() {{
	for (Map.Entry<MetadataField,String> ent : map.entrySet()) {
	  put(ent.getKey().getKey(), ent.getValue());
	}
      }};
  }
      

  class MyFileMetadataExtractor implements FileMetadataExtractor {
    List<MetadataTarget> targets = new ArrayList<MetadataTarget>();
    List<CachedUrl> cus = new ArrayList<CachedUrl>();
    Map<MetadataField,String> amProps;

    MyFileMetadataExtractor(Map<MetadataField,String> amProps) {
      this.amProps = amProps;
    }

    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
	throws IOException, PluginException {

      ArticleMetadata am = new ArticleMetadata();
      for (Map.Entry<MetadataField,String> ent : amProps.entrySet()) {
	am.put(ent.getKey(), ent.getValue());
      }
      emitter.emitMetadata(cu, am);
    }
  }

}
