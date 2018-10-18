/*
 * $Id:$
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

package org.lockss.plugin.silverchair.iwap;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.silverchair.ScHtmlHttpResponseHandler.ScRetryableNetworkException;
import org.lockss.util.Logger;

import org.lockss.util.urlconn.CacheException;
import org.lockss.util.urlconn.CacheResultHandler;
import org.lockss.util.urlconn.CacheResultMap;

public class IwapHtmlHttpResponseHandler implements CacheResultHandler {
  private static final Logger log = Logger.getLogger(IwapHtmlHttpResponseHandler.class);
  
  @Override
  public void init(final CacheResultMap map) throws PluginException {
    log.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to IwapHttpResponseHandler.init()");

  }

  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, final int code) throws PluginException {
    log.debug(code + ": " + url);
    switch (code) {
      default:
        log.warning("Unexpected responseCode (" + code + ") in handleResult(): AU " + au.getName() + "; URL " + url);
        throw new UnsupportedOperationException("Unexpected responseCode (" + code + ")");
    }
  }

  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, final Exception ex)
    throws PluginException {
    log.debug(ex.getMessage() + ": " + url);
    
    if (ex instanceof javax.net.ssl.SSLHandshakeException) {
      log.debug3("Retrying SSLHandshakeException", ex);
      //extends CacheException.RetryableNetworkException_3_10S
      return new ScRetryableNetworkException(ex);
    }
    
    // we should only get in here cases that we specifically map, report and retry/no fail/no store
    log.warning("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
    return new ScRetryableNetworkException(ex);
  }
  
}