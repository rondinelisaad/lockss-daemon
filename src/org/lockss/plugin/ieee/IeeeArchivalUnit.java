package org.lockss.plugin.ieee;

import org.lockss.plugin.base.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.util.*;

import java.util.*;
import java.net.*;

import gnu.regexp.*;
import org.lockss.state.*;

/**
 * <p>IeeeArchivalUnit: The Archival Unit Class for IEEE Plugin</p>
 * @author Claire Griffin
 * @version 1.0
 */

public class IeeeArchivalUnit extends BaseArchivalUnit {
  /**
   * Configuration parameter for new content crawl interval
   */
  static final String AUPARAM_NEW_CONTENT_CRAWL = NEW_CONTENT_CRAWL_KEY;

  /**
   * Configuration parameter for pause time between fetchs.
   */
  public static final String AUPARAM_PAUSE_TIME = PAUSE_TIME_KEY;
  private static final long DEFAULT_PAUSE_TIME = 10 * Constants.SECOND;

  protected Logger logger = Logger.getLogger("IeeeArchivalUnit");

  private int puNumber;             // the publication number
  private int puYear;               // the publication year

  protected IeeeArchivalUnit(Plugin myPlugin) {
    super(myPlugin);
  }


 protected void setAuParams(Configuration config)
      throws ArchivalUnit.ConfigurationException {

    puNumber = configMap.getInt(IeeePlugin.AUPARAM_PUNUM, -1);
    if (puNumber < 0) {
      throw new ConfigurationException("Publication Number - Out of Range.");
    }

    puYear = configMap.getInt(IeeePlugin.AUPARAM_YEAR, -1);
    if (puYear < 2003) {
      throw new ConfigurationException("Volume Year - Out of Range.");
    }

  }

  protected String makeName() {
    StringBuffer name = new StringBuffer(baseUrl.getHost());
    name.append(", puNumber ");
    name.append(puNumber);
    name.append(", ");
    name.append(puYear);
    return name.toString();
  }


  protected String makeStartUrl() {
    String ret;
    StringBuffer sb = new StringBuffer();
    sb.append(baseUrl.toString());
    sb.append("xpl/RecentIssue.jsp?puNumber=");
    sb.append(puNumber);
    sb.append("&year=");
    sb.append(puYear);
    ret = sb.toString();
    logger.debug("starting url is "+ ret);
    return ret;
  }

  protected CrawlRule makeRules()
      throws REException {
    List rules = new LinkedList();
    final int incl = CrawlRules.RE.MATCH_INCLUDE;
    final int excl = CrawlRules.RE.MATCH_EXCLUDE;
    String base = baseUrl.toString();
    if(base.endsWith("/"))
      base = base.substring(0, base.length()-1);

    rules.add(new CrawlRules.RE("^" + base, CrawlRules.RE.NO_MATCH_EXCLUDE));

    // include the stuff that determines how the page looks
    rules.add(new CrawlRules.RE(".*.gif", incl)); // gifs
    rules.add(new CrawlRules.RE(".*.jpg", incl)); // jpgs

    // include the toc for the archival year
    rules.add(new CrawlRules.RE(base +"/xpl/RecentIssue.jsp.*puNumber="
                                + puNumber+".*year=" +puYear, incl));

    // issue page
    rules.add(new CrawlRules.RE(base + "/xpl/tocresult.jsp.*isNumber=.*", incl));
    // printable issue page
    rules.add(new CrawlRules.RE(base + "/xpl/tocprint.jsp.*isNumber=.*", incl));

    // article -pdf
    rules.add(new CrawlRules.RE(base + "/iel5/" + puNumber + "/.*.pdf", incl));

   // abstracts
    rules.add(new CrawlRules.RE(base + ":80/xpls/abs_all.jsp.*", incl));

    logger.debug("Rules: " + rules);
    return new CrawlRules.FirstMatch(rules);
  }
}
