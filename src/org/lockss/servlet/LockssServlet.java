/*
 * $Id: LockssServlet.java,v 1.101.2.2 2008-09-09 08:02:51 tlipkis Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.servlet;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.collections.*;
import org.apache.commons.collections.bidimap.*;
import org.apache.commons.collections.iterators.*;
import org.mortbay.html.*;
import org.mortbay.servlet.MultiPartRequest;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.remote.RemoteApi;
import org.lockss.servlet.BatchAuConfig.Verb;
import org.lockss.util.*;

/** Abstract base class for LOCKSS servlets
 */
// SingleThreadModel causes servlet instances to be assigned to only a
// single thread (request) at a time.
public abstract class LockssServlet extends HttpServlet
  implements SingleThreadModel {

  // Constants
  static final String PARAM_LOCAL_IP = Configuration.PREFIX + "localIPAddress";

  static final String PARAM_PLATFORM_VERSION =
    Configuration.PREFIX + "platform.version";

  /** Inactive HTTP session (cookie) timeout */
  static final String PARAM_UI_SESSION_TIMEOUT =
    Configuration.PREFIX + "ui.sessionTimeout";
  static final long DEFAULT_UI_SESSION_TIMEOUT = 2 * Constants.DAY;

  /** Maximum size of uploaded file accepted */
  static final String PARAM_MAX_UPLOAD_FILE_SIZE =
    Configuration.PREFIX + "ui.maxUploadFileSize";
  static final int DEFAULT_MAX_UPLOAD_FILE_SIZE = 500000;

  /** The warning string to display when the UI is disabled. */
  static final String PARAM_UI_WARNING =
    Configuration.PREFIX + "ui.warning";

  // Name given to form element whose value is the action that should be
  // performed when the form is submitted.  (Not always the submit button.)
  public static final String ACTION_TAG = "lockssAction";

  public static final String JAVASCRIPT_RESOURCE =
    "org/lockss/htdocs/admin.js";

  public static final String ATTR_INCLUDE_SCRIPT = "IncludeScript";
  public static final String ATTR_ALLOW_ROLES = "AllowRoles";

  public static final String ROLE_ADMIN = "adminRole";
  public static final String ROLE_DEBUG = "debugRole";

  protected static Logger log = Logger.getLogger("LockssServlet");

  protected ServletContext context;

  private LockssApp theApp = null;
  private ServletManager servletMgr;

  // Request-local storage.  Convenient, but requires servlet instances
  // to be single threaded, and must ensure reset them to avoid carrying
  // over state between requests.
  protected HttpServletRequest req;
  protected HttpServletResponse resp;
  protected URL reqURL;
  protected HttpSession session;
  private String adminDir = null;
  protected String clientAddr;	// client addr, even if no param
  protected String localAddr;
  protected MultiPartRequest multiReq;

  private Vector footnotes;
  private int footNumber;
  private int tabindex;
  ServletDescr _myServletDescr = null;
  private String myName = null;

  // number submit buttons sequentially so unit tests can find them
  protected int submitButtonNumber = 0;

  /** Run once when servlet loaded. */
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    context = config.getServletContext();
    theApp =
      (LockssApp)context.getAttribute(ServletManager.CONTEXT_ATTR_LOCKSS_APP);
    servletMgr =
      (ServletManager)context.getAttribute(ServletManager.CONTEXT_ATTR_SERVLET_MGR);
  }

  public ServletManager getServletManager() {
    return servletMgr;
  }

  protected ServletDescr[] getServletDescrs() {
    return servletMgr.getServletDescrs();
  }

  /** Servlets must implement this method. */
  protected abstract void lockssHandleRequest()
      throws ServletException, IOException;

  /** Common request handling. */
  public void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    multiReq = null;
    try {
      this.req = req;
      this.resp = resp;
      if (log.isDebug()) {
	logParams();
      }
      resp.setContentType("text/html");
      // Don't want anything served by a servlet to be cached
      resp.setHeader("pragma", "no-cache");
      resp.setHeader("Cache-control", "no-cache");

      footNumber = 0;
      tabindex = 1;
      reqURL = new URL(UrlUtil.getRequestURL(req));
      clientAddr = getLocalIPAddr();
      submitButtonNumber = 0;

      // check whether servlet requires admin user
      if (myServletDescr().isAdminOnly() && !isAdminUser()) {
	displayWarningInLieuOfPage("You are not authorized to use " +
				   myServletDescr().heading);
	return;
      }

      // check whether servlet is disabled
      String reason =
	ServletUtil.servletDisabledReason(myServletDescr().getServletName());
      if (reason != null) {
	displayWarningInLieuOfPage("This function is disabled. " + reason);
	return;
      }
      lockssHandleRequest();
    } catch (ServletException e) {
      log.error("Servlet threw", e);
      throw e;
    } catch (IOException e) {
      log.error("Servlet threw", e);
      throw e;
    } catch (RuntimeException e) {
      log.error("Servlet threw", e);
      throw e;
    } finally {
      resetMyLocals();
      resetLocals();
    }
  }

  protected void resetLocals() {
  }

  protected void resetMyLocals() {
    // Don't hold on to stuff forever
    req = null;
    resp = null;
    session = null;
    reqURL = null;
    adminDir = null;
    localAddr = null;
    footnotes = null;
    _myServletDescr = null;
    myName = null;
    multiReq = null;
  }

  /** Set the session timeout to the configured value */
  protected void setSessionTimeout(HttpSession session) {
    Configuration config = CurrentConfig.getCurrentConfig();
    setSessionTimeout(session,
		      config.getTimeInterval(PARAM_UI_SESSION_TIMEOUT,
					     DEFAULT_UI_SESSION_TIMEOUT));
  }

  /** Set the session timeout */
  protected void setSessionTimeout(HttpSession session, long time) {
    session.setMaxInactiveInterval((int)(time  / Constants.SECOND));
  }

  /** Get the current session, creating it if necessary (and set the
   * timeout if so) */
  protected HttpSession getSession() {
    if (session == null) {
      session = req.getSession(true);
      if (session.isNew()) {
	setSessionTimeout(session);
      }
    }
    return session;
  }

  /** Return true iff a session has already been established */
  protected boolean hasSession() {
    return req.getSession(false) != null;
  }

  static final String SESSION_KEY_OBJECT_ID = "obj_id";
  static final String SESSION_KEY_OBJ_MAP = "obj_map";

  /** Get an unused ID string for storing an object in the session */
  protected String getNewSessionObjectId() {
    HttpSession session = getSession();
    synchronized (session) {
      Integer id = (Integer)getSession().getAttribute(SESSION_KEY_OBJECT_ID);
      if (id == null) {
	id = new Integer(1);
      }
      session.setAttribute(SESSION_KEY_OBJECT_ID,
			   new Integer(id.intValue() + 1));
      return id.toString();
    }
  }    

  /** Get the object associated with the ID in the session */
  protected Object getSessionIdObject(String id) {
    HttpSession session = getSession();
    synchronized (session) {
      BidiMap map = (BidiMap)session.getAttribute(SESSION_KEY_OBJ_MAP);
      if (map == null) {
	return null;
      }
      return map.getKey(id);
    }
  }

  /** Get the String associated with the ID in the session */
  protected String getSessionIdString(String id) {
    return (String)getSessionIdObject(id);
  }

  /** Get the ID with which the object is associated with the session, if
   * any */
  protected String getSessionObjectId(Object obj) {
    HttpSession session = getSession();
    BidiMap map;
    synchronized (session) {
      map = (BidiMap)session.getAttribute(SESSION_KEY_OBJ_MAP);
      if (map == null) {
	map = new DualHashBidiMap();
	session.setAttribute(SESSION_KEY_OBJ_MAP, map);
      }
    }
    synchronized (map) {
      String id = (String)map.get(obj);
      if (id == null) {
	id = getNewSessionObjectId();
	map.put(obj, id);
      }
      return id;
    }
  }

  // Return descriptor of running servlet
  protected ServletDescr myServletDescr() {
    if (_myServletDescr == null) {
      _myServletDescr = servletMgr.findServletDescr(this);
    }
    return _myServletDescr;
  }

  // By default, servlet heading is in descr.  Override method to
  // compute other heading
  protected String getHeading(ServletDescr d) {
    if (d == null) return "Unknown Servlet";
    return d.heading;
  }

  protected String getHeading() {
    return getHeading(myServletDescr());
  }

  String getLocalIPAddr() {
    if (localAddr == null) {
      try {
	IPAddr localHost = IPAddr.getLocalHost();
	localAddr = localHost.getHostAddress();
      } catch (UnknownHostException e) {
	// shouldn't happen
	log.error("LockssServlet: getLocalHost: " + e.toString());
	return "???";
      }
    }
    return localAddr;
  }

  // Return IP addr used by LCAP.  If specified by (misleadingly named)
  // localIPAddress prop, might not really be our address (if we are
  // behind NAT).
  String getLcapIPAddr() {
    String ip = CurrentConfig.getParam(PARAM_LOCAL_IP);
    if (ip == null || ip.length() <= 0)  {
      return getLocalIPAddr();
    }
    return ip;
  }

  String getMachineName() {
    if (myName == null) {
      // Return the canonical name of the interface the request was aimed
      // at.  (localIPAddress prop isn't necessarily right here, as it
      // might be the address of a NAT that we're behind.)
      String host = reqURL.getHost();
      try {
	IPAddr localHost = IPAddr.getByName(host);
	String ip = localHost.getHostAddress();
	myName = getMachineName(ip);
      } catch (UnknownHostException e) {
	// shouldn't happen
	log.error("getMachineName", e);
	return host;
      }
    }
    return myName;
  }

  String getMachineName(String ip) {
    try {
      IPAddr inet = IPAddr.getByName(ip);
      return inet.getHostName();
    } catch (UnknownHostException e) {
      log.warning("getMachineName", e);
    }
    return ip;
  }

  // return IP given name or IP
  String getMachineIP(String name) {
    try {
      IPAddr inet = IPAddr.getByName(name);
      return inet.getHostAddress();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  boolean isServletLinkInNav(ServletDescr d) {
    return !isThisServlet(d) || linkMeInNav();
  }

  boolean isThisServlet(ServletDescr d) {
    return d == myServletDescr();
  }

  /** servlets may override this to determine whether they should be
   * a link in nav table */
  protected boolean linkMeInNav() {
    return false;
  }

  boolean isLargeLogo() {
    return myServletDescr().isLargeLogo();
  }

  // user predicates
  protected boolean isDebugUser() {
    return req.isUserInRole(ROLE_DEBUG) &&
      StringUtil.isNullString(req.getParameter("nodebug"));
  }

  protected boolean isAdminUser() {
    if (req.isUserInRole(ROLE_ADMIN) &&
	StringUtil.isNullString(req.getParameter("noadmin"))) {
      return true;
    }
    List roles = (List)context.getAttribute(ATTR_ALLOW_ROLES);
    if (roles != null) {
      return roles.contains(ROLE_ADMIN);
    }
    return false;
  }

  // Called when a servlet doesn't get the parameters it expects/needs
  protected void paramError() throws IOException {
    // FIXME: As of 2006-03-15 this method and its only caller checkParam() are not called from anywhere
    PrintWriter wrtr = resp.getWriter();
    Page page = new Page();
    // add referer, params, msg to contact lockss unless from old bookmark
    // or manually entered url
    page.add("Parameter error");
    page.write(wrtr);
  }

  // return true iff error
  protected boolean checkParam(boolean ok, String msg) throws IOException {
    if (ok) return false;
    log.error(myServletDescr().getPath() + ": " + msg);
    paramError();
    return true;
  }

  /** Construct servlet URL
   */
  String srvURL(ServletDescr d) {
    return srvURL(null, d, null);
  }

  /** Construct servlet URL with params
   */
  String srvURL(ServletDescr d, String params) {
    return srvURL(null, d, params);
  }

  /** Construct servlet URL with params
   */
  String srvURL(ServletDescr d, Properties params) {
    return srvURL(null, d, concatParams(params));
  }

  /** Construct servlet absolute URL, with params as necessary.
   */
  String srvAbsURL(ServletDescr d, String params) {
    return srvURL(getMachineName(), d, params);
  }

  /** Construct servlet URL, with params as necessary.  Avoid generating a
   *  hostname different from that used in the original request, or
   *  browsers will prompt again for login
   */
  String srvURL(String host, ServletDescr d, String params) {
    if (d.isPathIsUrl()) {
      return d.getPath();
    }
    StringBuffer sb = new StringBuffer();
    StringBuffer paramsb = new StringBuffer();

    if (params != null) {
      paramsb.append('&');
      paramsb.append(params);
    }
    if (host != null) {
      sb.append(reqURL.getProtocol());
      sb.append("://");
      sb.append(host);
      sb.append(':');
      sb.append(reqURL.getPort());
    }
    sb.append('/');
    sb.append(d.getPath());
    if (paramsb.length() != 0) {
      paramsb.setCharAt(0, '?');
      sb.append(paramsb.toString());
    }
    return sb.toString();
  }

  /** Return a link to a servlet */
  String srvLink(ServletDescr d, String text) {
    return srvLink(d, text, (String)null);
  }

  /** Return a link to a servlet with params */
  String srvLink(ServletDescr d, String text, String params) {
    return new Link(srvURL(d, params),
		    (text != null ? text : d.heading)).toString();
  }

  /** Return a link to a servlet with params */
  String srvLink(ServletDescr d, String text, Properties params) {
    return new Link(srvURL(d, params),
		    text).toString();
  }

  /** Return an absolute link to a servlet with params */
  String srvAbsLink(ServletDescr d, String text, String params) {
    return new Link(srvAbsURL(d, params),
		    (text != null ? text : d.heading)).toString();
  }

  /** Return text as a link iff isLink */
  String conditionalSrvLink(ServletDescr d, String text, String params,
			    boolean isLink) {
    if (isLink) {
      return srvLink(d, text, params);
    } else {
      return text;
    }
  }

  /** Return text as a link iff isLink */
  String conditionalSrvLink(ServletDescr d, String text, boolean isLink) {
    return conditionalSrvLink(d, text, null, isLink);
  }

  /** Concatenate params for URL string */
  static String concatParams(String p1, String p2) {
    if (StringUtil.isNullString(p1)) {
      return p2;
    }
    if (StringUtil.isNullString(p2)) {
      return p1;
    }
    return p1 + "&" + p2;
  }

  /** Concatenate params for URL string */
  String concatParams(Properties props) {
    if (props == null) {
      return null;
    }
    java.util.List list = new ArrayList();
    for (Iterator iter = props.keySet().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      String val = props.getProperty(key);
      if (!StringUtil.isNullString(val)) {
	list.add(key + "=" + urlEncode(val));
      }
    }
    return StringUtil.separatedString(list, "&");
  }

  String modifyParams(String key, String val) {
    Properties props = getParamsAsProps();
    props.setProperty(key, val);
    return concatParams(props);
  }

  Properties getParamsAsProps() {
    Properties props = new Properties();
    for (Enumeration en = req.getParameterNames(); en.hasMoreElements(); ) {
      String name = (String)en.nextElement();
      props.setProperty(name, req.getParameter(name));
    }
    return props;
  }

  protected String urlEncode(String param) {
    return UrlUtil.encodeUrl(param);
  }


  protected boolean isServletAvailable(ServletDescr d) {
    if (!isDebugUser() && d.isDebugOnly()) return false;
    if (!isAdminUser() && d.isAdminOnly()) return false;
    if (d.cls == ServletDescr.UNAVAILABLE_SERVLET_MARKER) return false;
    return true;
  }

  protected boolean isServletInNav(ServletDescr d) {
    return isServletAvailable(d) && d.isInNav(this);
  }

  protected String getRequestKey() {
    String key = req.getPathInfo();
    if (key != null && key.startsWith("/")) {
      return key.substring(1);
    }
    return key;
  }

  /** Common page setup. */
  protected Page newPage() {
    // Compute heading
    String heading = getHeading();
    if (heading == null) {
      heading = "Box Administration";
    }

    // Create page and layout header
    Page page = ServletUtil.doNewPage(getPageTitle(), isFramed());
    FilterIterator inNavIterator = new FilterIterator(
        new ObjectArrayIterator(getServletDescrs()),
        new Predicate() {
          public boolean evaluate(Object obj) {
            return isServletInNav((ServletDescr)obj);
          }
        });
    ServletUtil.layoutHeader(this,
                             page,
                             heading,
                             isLargeLogo(),
                             getMachineName(),
                             getMachineName(clientAddr),
                             getLockssApp().getStartDate(),
                             inNavIterator);
    String warnMsg = CurrentConfig.getParam(PARAM_UI_WARNING);
    if (warnMsg != null) {
      Composite warning = new Composite();
      warning.add("<center><font color=red size=+1>");
      warning.add(warnMsg);
      warning.add("</font></center><br>");
      page.add(warning);
    }
    return page;
  }

  protected Page addBarePageHeading(Page page) {
// FIXME: Move the following fragment elsewhere
// It causes the doctype statement to appear in the middle,
// after the <body> tag.
    page.add("<!doctype html public \"-//w3c//dtd html 4.0 transitional//en\">");
    page.addHeader("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">");
    page.addHeader("<meta http-equiv=\"content-type\" content=\"text/html;charset=ISO-8859-1\">");
    page.addHeader("<link rel=\"shortcut icon\" href=\"/favicon.ico\" type=\"image/x-icon\" />");
    return page;
  }

  private boolean isFramed = false;

  protected String errMsg;

  protected String statusMsg;

  protected boolean isFramed() {
    return isFramed;
  }

  protected void setFramed(boolean v) {
    isFramed = v;
  }

  protected String getPageTitle() {
    String heading = getHeading();
    if (heading != null) {
      return "LOCKSS: " + heading;
    } else {
      return "LOCKSS";
    }
  }

  /** Return a button that invokes the javascript submit routine with the
   * specified action */
  protected Element submitButton(String label, String action) {
    return submitButton(label, action, null, null);
  }

  /** Return a button that invokes javascript when clicked. */
  Input jsButton(String label, String js) {
    Input btn = new Input("button", null);
    btn.attribute("value", label);
    setTabOrder(btn);
    btn.attribute("onClick", js);
    return btn;
  }

  /** Return a button that invokes the javascript submit routine with the
   * specified action, first storing the value in the specified form
   * prop. */
  protected Element submitButton(String label, String action,
				 String prop, String value) {
    StringBuffer sb = new StringBuffer(40);
    sb.append("lockssButton(this, '");
    sb.append(action);
    sb.append("'");
    if (prop != null && value != null) {
      sb.append(", '");
      sb.append(prop);
      sb.append("', '");
      sb.append(value);
      sb.append("'");
    }
    sb.append(")");
    Input btn = jsButton(label, sb.toString());
    btn.attribute("id", "lsb." + (++submitButtonNumber));
    return btn;
  }

  /** Return a (possibly labelled) checkbox.
   * @param label appears to right of checkbox if non null
   * @param value value included in result set if box checked
   * @param key form key to which result set is assigned
   * @param checked if true, box is initially checked
   * @return a checkbox Element
   */
  Element checkBox(String label, String value, String key, boolean checked) {
    Input in = new Input(Input.Checkbox, key, value);
    if (checked) {
      in.check();
    }
    setTabOrder(in);
    if (StringUtil.isNullString(label)) {
      return in;
    } else {
      Composite c = new Composite();
      c.add(in);
      c.add(" ");
      c.add(label);
      return c;
    }
  }

  /** Return a labelled rasio button
   * @param label label to right of circle, and form value if checked
   * @param key form key to which value is assigned
   * @param checked if true, is initially checked
   * @return a readio button Element
   */
  protected Element radioButton(String label, String key, boolean checked) {
    return radioButton(label, label, key, checked);
  }

  /** Return a labelled rasio button
   * @param label appears to right of circle if non null
   * @param value value assigned to key if box checked
   * @param key form key to which value is assigned
   * @param checked if true, is initially checked
   * @return a readio button Element
   */
  protected Element radioButton(String label, String value,
			       String key, boolean checked) {
    Composite c = new Composite();
    Input in = new Input(Input.Radio, key, value);
    if (checked) {
      in.check();
    }
    setTabOrder(in);
    c.add(in);
    c.add(" ");
    c.add(label);
    return c;
  }

  /** Add html tags to grey the text if isGrey is true */
  protected String greyText(String txt, boolean isGrey) {
    if (!isGrey) {
      return txt;
    }
    return "<font color=gray>" + txt + "</font>";
  }

  /** Set this element next in the tab order.  Returns the element for
   * easier nesting in expressions. */
  protected Element setTabOrder(Element ele) {
    ele.attribute("tabindex", tabindex++);
    return ele;
  }

  /** Store a footnote, assign it a number, return html for footnote
   * reference.  If footnote in null or empty, no footnote is added and am
   * empty string is returned.  Footnote numbers get turned into links;
   * <b>Do not put the result of addFootnote inside a link!</b>.  */
  protected String addFootnote(String s) {
    if (s == null || s.length() == 0) {
      return "";
    }
    if (footNumber == 0) {
      if (footnotes == null) {
	footnotes = new Vector(10, 10);
      } else {
	footnotes.removeAllElements();
      }
    }
    int n = footnotes.indexOf(s);
    if (n < 0) {
      n = footNumber++;
      footnotes.addElement(s);
    }
    return "<sup><font size=-1><a href=#foottag" + (n+1) + ">" +
      (n+1) + "</a></font></sup>";
  }

  /** Add javascript to page.  Normally adds a link to the script file, but
   * can be told to include the script directly in the page, to accomodate
   * unit testing of individual servlets, when other fetches won't work. */
  protected void addJavaScript(Composite comp) {
    String include = (String)context.getAttribute(ATTR_INCLUDE_SCRIPT);
    if (StringUtil.isNullString(include)) {
      linkToJavaScript(comp);
    } else {
      includeJavaScript0(comp);
    }
  }

  private void includeJavaScript0(Composite comp) {
    Script script = new Script(getJavascript());
    comp.add(script);
  }

  private void linkToJavaScript(Composite comp) {
    Script script = new Script("");
    script.attribute("src", "admin.js");
    comp.add(script);
  }

  private static String jstext = null;

  private static synchronized String getJavascript() {
    if (jstext == null) {
      try {
	ClassLoader loader = Thread.currentThread().getContextClassLoader();
	InputStream istr = loader.getResourceAsStream(JAVASCRIPT_RESOURCE);
	jstext = StringUtil.fromInputStream(istr);
	istr.close();
      } catch (Exception e) {
	log.error("Can't load javascript", e);
      }
    }
    return jstext;
  }

  /** Display a message in lieu of the normal page
   */
  protected void displayMsgInLieuOfPage(String msg) throws IOException {
    // TODO: Look at HTML
    Page page = newPage();
    Composite warning = new Composite();
    warning.add(msg);
    warning.add("<br>");
    page.add(warning);
    layoutFooter(page);
    page.write(resp.getWriter());
  }

  /** Display a warning in red, in lieu of the normal page
   */
  protected void displayWarningInLieuOfPage(String msg) throws IOException {
    displayMsgInLieuOfPage("<center><font color=red size=+1>" + msg +
			   "</font></center>");
  }

  /** Display "The cache isn't ready yet, come back later"
   */
  protected void displayNotStarted() throws IOException {
    displayWarningInLieuOfPage("This LOCKSS box is still starting.  Please "
			       + srvLink(myServletDescr(), "try again",
					 getParamsAsProps())
			       + " in a moment.");
  }

  public MultiPartRequest getMultiPartRequest()
      throws FormDataTooLongException, IOException {
    int maxUpload = CurrentConfig.getIntParam(PARAM_MAX_UPLOAD_FILE_SIZE,
					      DEFAULT_MAX_UPLOAD_FILE_SIZE);
    return getMultiPartRequest(maxUpload);
  }

  public MultiPartRequest getMultiPartRequest(int maxLen)
      throws FormDataTooLongException, IOException {
    if (req.getContentType() == null ||
	!req.getContentType().startsWith("multipart/form-data")) {
      return null;
    }
    if (req.getContentLength() > maxLen) {
      throw new FormDataTooLongException(req.getContentLength() + " bytes, " +
					 maxLen + " allowed");
    }
    MultiPartRequest multi = new MultiPartRequest(req);
    if (log.isDebug2()) {
      String[] parts = multi.getPartNames();
      log.debug3("Multipart request, " + parts.length + " parts");
      if (log.isDebug3()) {
	for (int p = 0; p < parts.length; p++) {
	  String name = parts[p];
	  String cont = multi.getString(parts[p]);
	  log.debug3(name + ": " + cont);
	}
      }
    }
    multiReq = multi;
    return multi;
  }

  public String getParameter(String name) {
    String val = req.getParameter(name);
    if (val == null && multiReq != null) {
      val = multiReq.getString(name);
    }
    if (StringUtil.isNullString(val)) {
      return null;
    }
    return val;
  }

  protected void layoutFooter(Page page) {
    ServletUtil.doLayoutFooter(page,
                              (footnotes == null ? null : footnotes.iterator()),
                              getLockssApp().getVersionInfo());
    if (footnotes != null) {
      footnotes.removeAllElements();
    }
  }

  /** Return the app instance.
   */
  protected LockssApp getLockssApp() {
    return theApp;
  }

  /** Return the daemon instance, assumes that the servlet is running in
   * the daemon.
   * @throws ClassCastException if the servlet is running in an app other
   * than the daemon
   */
  protected LockssDaemon getLockssDaemon() {
    return (LockssDaemon)theApp;
  }

  protected void logParams() {
    Enumeration en = req.getParameterNames();
    while (en.hasMoreElements()) {
      String name = (String)en.nextElement();
      String vals[];
      if (log.isDebug2() && (vals = req.getParameterValues(name)).length > 1) {
	log.debug(name + " = " + StringUtil.separatedString(vals, ", "));
      } else {
	log.debug(name + " = " + req.getParameter(name));
      }
    }
  }

  /** Convenience method */
  protected String encodeText(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_TEXT);
  }

  /** Convenience method */
  protected String encodeTextArea(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_TEXTAREA);
  }

  /** Convenience method */
  protected String encodeAttr(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_ATTR);
  }

  /** Create message and error message block
   * @param composite TODO*/
  protected void layoutErrorBlock(Composite composite) {
    if (errMsg != null || statusMsg != null) {
      ServletUtil.layoutErrorBlock(composite, errMsg, statusMsg);
    }
  }

  /** Exception thrown if multipart form data is longer than the
   * caller-supplied max */
  public static class FormDataTooLongException extends Exception {
    public FormDataTooLongException(String message) {
      super(message);
    }
  }

}
