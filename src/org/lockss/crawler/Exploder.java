/*
 * $Id: Exploder.java,v 1.1.2.2 2007-09-15 02:49:51 dshr Exp $
 */

/*

Copyright (c) 2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.crawler;

import java.io.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.exploded.*;
import org.lockss.crawler.BaseCrawler;
import org.lockss.config.Configuration;
import org.lockss.app.LockssDaemon;
import org.lockss.state.HistoryRepository;
import org.lockss.plugin.UrlCacher;

/**
 * The abstract base class for exploders, which handle extracting files from
 * archives.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public abstract class Exploder {
  private static Logger logger = Logger.getLogger("Exploder");
  protected UrlCacher urlCacher;
  protected int maxRetries;
  protected CrawlSpec crawlSpec;
  protected BaseCrawler crawler;
  protected boolean explodeFiles;
  protected boolean storeArchive;
  protected String archiveUrl = null;
  protected Hashtable addTextTo = null;
  protected PluginManager pluginMgr = null;
  protected Hashtable touchedAus = new Hashtable();

  /**
   * Constructor
   * @param uc UrlCacher for the archive
   * @param maxRetries
   * @param crawlSpec the CrawlSpec for the crawl that found the archive
   * @param crawler the crawler that found the archive
   * @param explode true to explode the archives
   * @param store true to store the archive as well
   */
  protected Exploder(UrlCacher uc, int maxRetries, CrawlSpec crawlSpec,
		     BaseCrawler crawler, boolean explode, boolean store) {
    urlCacher = uc;
    this.maxRetries = maxRetries;
    this.crawlSpec = crawlSpec;
    this.crawler = crawler;
    archiveUrl = uc.getUrl();
    explodeFiles = explode;
    storeArchive = store;
    pluginMgr = crawler.getDaemon().getPluginManager();
  }

  /**
   * Explode the archive into its constituent elements
   */
  protected abstract void explodeUrl();

  protected void storeEntry(ArchiveEntry ae) throws IOException {
    // We assume that all exploded content is organized into
    // AUs which each contain only URLs starting with the AUs
    // base_url.  This allows ExplodedArchivalUnit to maintain
    // a map from a baseUrl to one of its AUs.
    ArchivalUnit au = null;
    String baseUrl = ae.getBaseUrl();
    String restOfUrl = ae.getRestOfUrl();
    CachedUrl cu = pluginMgr.findCachedUrl(baseUrl + restOfUrl, false);
    if (cu != null) {
      au = cu.getArchivalUnit();
      logger.debug(baseUrl + restOfUrl + " old au " + au.getAuId());
      cu.release();
    }
    if (au == null) {
      // There's no AU for this baseUrl,  so create one
      CIProperties props = ae.getAuProps();
      if (props == null) {
	props = new CIProperties();
	props.put(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
      }
      props.put(ConfigParamDescr.PUB_NEVER.getKey(), "true");
      String pluginName = ExplodedPlugin.class.getName();
      String key = PluginManager.pluginKeyFromName(pluginName);
      logger.debug3(pluginName + " has key: " + key);
      Plugin plugin = pluginMgr.getPlugin(key);
      if (plugin == null) {
	logger.error(pluginName + " key " + key + " not found");
	throw new IOException(pluginName + " not found");
      }
      logger.debug3(pluginName + ": " + plugin.toString());
      if (logger.isDebug3()) {
	for (Enumeration en = props.keys(); en.hasMoreElements(); ) {
	  String prop = (String)en.nextElement();
	  logger.debug3("AU prop key " + prop + " value " + props.get(prop));
	}
      }
      try {
	au = pluginMgr.createAndSaveAuConfiguration(plugin, props);
      } catch (ArchivalUnit.ConfigurationException ex) {
	logger.error("createAndSaveAuConfiguration() threw " + ex.toString());
	throw new IOException(pluginName + " not initialized for " + baseUrl);
      }
      if (au == null) {
	logger.error("Failed to create new AU", new Throwable());
	throw new IOException("Can't create au for " + baseUrl);
      }
    }
    if (!(au instanceof ExplodedArchivalUnit)) {
      logger.error("New AU not ExplodedArchivalUnit " + au.toString(),
		   new Throwable());
    }
    touchedAus.put(baseUrl, au);
    String newUrl = baseUrl + restOfUrl;
    // Create a new UrlCacher from the ArchivalUnit and store the
    // element using it.
    BaseUrlCacher newUc = (BaseUrlCacher)au.makeUrlCacher(newUrl);
    BitSet flags = newUc.getFetchFlags();
    flags.set(UrlCacher.DONT_CLOSE_INPUT_STREAM_FLAG);
    newUc.setFetchFlags(flags);
    // XXX either fetch or storeContent synthesizes some properties
    // XXX for the URL - check and move the place to storeContent
    logger.debug3("Storing " + newUrl + " in " + au.toString());
    newUc.storeContentIn(newUrl, ae.getInputStream(), ae.getHeaderFields());
    crawler.getCrawlStatus().signalUrlFetched(newUrl);
    // crawler.getCrawlStatus().signalMimeTypeOfUrl(newUrl, mimeType);
  }

  protected void handleAddText(ArchiveEntry ae) {
    Hashtable addText = ae.getAddText();
    String addBaseUrl = ae.getBaseUrl();
    if (addText != null) {
      if (addTextTo == null) {
	addTextTo = new Hashtable();
      }
      for (Enumeration en = addText.keys(); en.hasMoreElements(); ) {
	String restOfUrl = (String)en.nextElement();
	String newText = "";
	if (addTextTo.containsKey(addBaseUrl + restOfUrl)) {
	  newText = (String)addTextTo.get(addBaseUrl + restOfUrl);
	}
	newText += (String)addText.get(restOfUrl);
	addTextTo.put(addBaseUrl + restOfUrl, newText);
	logger.debug3("addText for " + addBaseUrl + restOfUrl +
		      " now " + newText);
      }
    }
  }

  protected void addText() {
    if (addTextTo != null) {
      for (Enumeration en = addTextTo.keys(); en.hasMoreElements(); ) {
	String url = (String) en.nextElement();
	String text = (String) addTextTo.get(url);

	CachedUrl cu = pluginMgr.findCachedUrl(url, false);
	if (cu == null) {
	  logger.error("Trying to update page outside AU" +
		       url);
	} else {
	  addTextToPage(cu, url, text);
	  cu.release();
	}
      }
    }
  }

  protected void addTextToPage(CachedUrl cu, String url, String text) {
    ArchivalUnit au = cu.getArchivalUnit();
    if (!cu.hasContent()) {
      // Create a new page
      logger.debug3("Create new page " + url + " in " + au.getAuId());
      // XXX
    }
    logger.debug3("Adding text " + text + " to page " + url +
		  " in " + au.getAuId());
    // XXX add the text to the page
  }

  protected class DefaultExploderHelper implements ExploderHelper {
    private UrlCacher uc = null;
    private CrawlSpec crawlSpec = null;
    private Crawler crawler = null;
    private LockssDaemon theDaemon;

    DefaultExploderHelper(UrlCacher uc, CrawlSpec crawlSpec,
			  BaseCrawler crawler) {
      this.uc = uc;
      this.crawlSpec = crawlSpec;
      this.crawler = crawler;
    }

    public void process(ArchiveEntry ae) {
      // By default the files have to go in the crawler's AU
      ArchivalUnit au = crawler.getAu();
      // By default the path should start at the AU's base url.
      Configuration config = au.getConfiguration();
      String url = config.get(ConfigParamDescr.BASE_URL.getKey());
      ae.setBaseUrl(url);
      ae.setRestOfUrl(ae.getName());
      CIProperties cip = new CIProperties();
      // XXX do something here to invent header fields
      ae.setHeaderFields(cip);
    }

    public void setDaemon(LockssDaemon daemon) {
      theDaemon = daemon;
    }
  }
}
