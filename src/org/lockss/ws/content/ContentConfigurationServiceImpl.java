/*

 Copyright (c) 2014-2018 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.ws.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jws.WebService;
import org.lockss.app.LockssDaemon;
import org.lockss.config.Configuration;
import org.lockss.daemon.TitleConfig;
import org.lockss.remote.RemoteApi;
import org.lockss.remote.RemoteApi.BatchAuStatus;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.lockss.ws.entities.ContentConfigurationResult;
import org.lockss.ws.entities.LockssWebServicesFault;

/**
 * The ContentConfiguration web service implementation.
 */
@WebService
public class ContentConfigurationServiceImpl implements
    ContentConfigurationService {
  private static Logger log =
      Logger.getLogger(ContentConfigurationServiceImpl.class);

  /**
   * Configures the archival unit defined by its identifier.
   * 
   * @param auId
   *          A String with the identifier (auid) of the archival unit. The
   *          archival unit to be added must already be in the title db that's
   *          loaded into the daemon.
   * @return a ContentConfigurationResult with the result of the operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public ContentConfigurationResult addAuById(String auId)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "addByAuId(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auId = " + auId);

    List<String> auIds = new ArrayList<String>();
    auIds.add(auId);

    ContentConfigurationResult result = addAusByIdList(auIds).get(0);
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Configures the archival units defined by a list of their identifiers.
   * 
   * @param auIds
   *          A {@code List<String>} with the identifiers (auids) of the archival units.
   *          The archival units to be added must already be in the title db
   *          that's loaded into the daemon.
   * @return a {@code List<ContentConfigurationResult>} with the results of the
   *         operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public List<ContentConfigurationResult> addAusByIdList(List<String> auIds)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "addAusByIdList(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auIds = " + auIds);

    List<ContentConfigurationResult> results =
	new ArrayList<ContentConfigurationResult>(auIds.size());

    RemoteApi remoteApi = LockssDaemon.getLockssDaemon().getRemoteApi();
    String[] auIdArray = new String[auIds.size()];
    int index = 0;

    Map<String, Configuration> titleConfigs =
	new HashMap<String, Configuration>();

    // Loop  through all the Archival Unit identifiers.
    for (String auId : auIds) {
      // Populate the array of Archival Unit identifiers.
      auIdArray[index++] = auId;

      // Get the configuration of the Archival Unit.
      TitleConfig titleConfig = remoteApi.findTitleConfig(auId);

      // Check whether the configuration was found.
      if (titleConfig != null) {
        // Yes: Add it to the map.
        titleConfigs.put(auId,  titleConfig.getConfig());
      }
    }

    // Add all the archival units.
    BatchAuStatus status = remoteApi.batchAddAus(RemoteApi.BATCH_ADD_ADD,
	auIdArray, null, null, titleConfigs, new HashMap<String, String>(),
	null);

    index = 0;

    // Loop through all the results.
    for (BatchAuStatus.Entry entry : status.getUnsortedStatusList()) {
      BatchAuStatus entryStatus = new BatchAuStatus();
      entryStatus.add(entry);

      ContentConfigurationResult result = null;

      if (entry.isOk()) {
        if (log.isDebug()) log.debug("Success configuring AU '"
  	  + entry.getName() + "': " + entry.getExplanation());

        result = new ContentConfigurationResult(auIdArray[index++],
            entry.getName(), Boolean.TRUE, entry.getExplanation());
      } else {
        log.error("Error configuring AU '" + entry.getName() + "': "
  	  + entry.getExplanation());

        result = new ContentConfigurationResult(auIdArray[index++],
            entry.getName(), Boolean.FALSE, entry.getExplanation());
      }

      results.add(result);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "results = " + results);
    return results;
  }

  /**
   * Unconfigures the archival unit defined by its identifier.
   * 
   * @param auId
   *          A String with the identifier (auid) of the archival unit.
   * @return a ContentConfigurationResult with the result of the operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public ContentConfigurationResult deleteAuById(String auId)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "deleteAuById(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auId = " + auId);

    List<String> auIds = new ArrayList<String>(1);
    auIds.add(auId);

    // Delete the archival unit.
    ContentConfigurationResult result = deleteAusByIdList(auIds).get(0);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Unconfigures the archival units defined by a list with their identifiers.
   * 
   * @param auIds
   *          A {@code List<String>} with the identifiers (auids) of the archival units.
   * @return a {@code List<ContentConfigurationResult>} with the results of the
   *         operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public List<ContentConfigurationResult> deleteAusByIdList(List<String> auIds)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "deleteAusByIdList(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auIds = " + auIds);

    List<ContentConfigurationResult> results =
	new ArrayList<ContentConfigurationResult>(auIds.size());

    // Delete the archival units.
    BatchAuStatus status =
	LockssDaemon.getLockssDaemon().getRemoteApi().deleteAus(auIds);

    // Loop through all the results.
    for (int i = 0; i < status.getUnsortedStatusList().size(); i++) {
      // Get the original Archival Unit identifier.
      String auId = auIds.get(i);

      // Handle the result.
      BatchAuStatus.Entry statusEntry = status.getUnsortedStatusList().get(i);

      if (statusEntry.isOk() || "Deleted".equals(statusEntry.getStatus())) {
	if (log.isDebug()) log.debug("Success unconfiguring AU '"
	    + statusEntry.getName() + "': " + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = "Deleted Archival Unit '" + auId + "'";
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	    Boolean.TRUE, statusEntry.getExplanation()));
      } else {
	log.error("Error unconfiguring AU '" + statusEntry.getName() + "': "
	    + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = statusEntry.getStatus();
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	  Boolean.FALSE, explanation));
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "results = " + results);
    return results;
  }

  /**
   * Reactivates the archival unit defined by its identifier.
   * 
   * @param auId
   *          A String with the identifier (auid) of the archival unit.
   * @return a ContentConfigurationResult with the result of the operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public ContentConfigurationResult reactivateAuById(String auId)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "reactivateAuById(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auId = " + auId);

    List<String> auIds = new ArrayList<String>(1);
    auIds.add(auId);

    // Reactivate the archival unit.
    ContentConfigurationResult result = reactivateAusByIdList(auIds).get(0);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * reactivates the archival units defined by a list with their identifiers.
   * 
   * @param auIds
   *          A {@code List<String>} with the identifiers (auids) of the archival units.
   * @return a {@code List<ContentConfigurationResult>} with the results of the
   *         operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public List<ContentConfigurationResult> reactivateAusByIdList(
      List<String> auIds) throws LockssWebServicesFault {
    final String DEBUG_HEADER = "reactivateAusByIdList(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auIds = " + auIds);

    List<ContentConfigurationResult> results =
	new ArrayList<ContentConfigurationResult>(auIds.size());

    // Reactivate the archival units.
    BatchAuStatus status =
	LockssDaemon.getLockssDaemon().getRemoteApi().reactivateAus(auIds);

    // Loop through all the results.
    for (int i = 0; i < status.getUnsortedStatusList().size(); i++) {
      // Get the original Archival Unit identifier.
      String auId = auIds.get(i);

      // Handle the result.
      BatchAuStatus.Entry statusEntry = status.getUnsortedStatusList().get(i);

      if (statusEntry.isOk() || "Added".equals(statusEntry.getStatus())) {
	if (log.isDebug()) log.debug("Success reactivating AU '"
	    + statusEntry.getName() + "': " + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = "Reactivated Archival Unit '" + auId + "'";
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	    Boolean.TRUE, statusEntry.getExplanation()));
      } else {
	log.error("Error reactivating AU '" + statusEntry.getName() + "': "
	    + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = statusEntry.getStatus();
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	  Boolean.FALSE, explanation));
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "results = " + results);
    return results;
  }

  /**
   * Deactivates the archival unit defined by its identifier.
   * 
   * @param auId
   *          A String with the identifier (auid) of the archival unit.
   * @return a ContentConfigurationResult with the result of the operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public ContentConfigurationResult deactivateAuById(String auId)
      throws LockssWebServicesFault {
    final String DEBUG_HEADER = "deactivateAuById(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auId = " + auId);

    List<String> auIds = new ArrayList<String>(1);
    auIds.add(auId);

    // Deactivate the archival unit.
    ContentConfigurationResult result = deactivateAusByIdList(auIds).get(0);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Deactivates the archival units defined by a list with their identifiers.
   * 
   * @param auIds
   *          A {@code List<String>} with the identifiers (auids) of the archival units.
   * @return a {@code List<ContentConfigurationResult>} with the results of the
   *         operation.
   * @throws LockssWebServicesFault
   */
  @Override
  public List<ContentConfigurationResult> deactivateAusByIdList(
      List<String> auIds) throws LockssWebServicesFault {
    final String DEBUG_HEADER = "deactivateAusByIdList(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "auIds = " + auIds);

    List<ContentConfigurationResult> results =
	new ArrayList<ContentConfigurationResult>(auIds.size());

    // Deactivate the archival units.
    BatchAuStatus status =
	LockssDaemon.getLockssDaemon().getRemoteApi().deactivateAus(auIds);

    // Loop through all the results.
    for (int i = 0; i < status.getUnsortedStatusList().size(); i++) {
      // Get the original Archival Unit identifier.
      String auId = auIds.get(i);

      // Handle the result.
      BatchAuStatus.Entry statusEntry = status.getUnsortedStatusList().get(i);

      if (statusEntry.isOk() || "Deactivated".equals(statusEntry.getStatus())) {
	if (log.isDebug()) log.debug("Success deactivating AU '"
	    + statusEntry.getName() + "': " + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = "Deactivated Archival Unit '" + auId + "'";
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	    Boolean.TRUE, statusEntry.getExplanation()));
      } else {
	log.error("Error deactivating AU '" + statusEntry.getName() + "': "
	    + statusEntry.getExplanation());

	String explanation = statusEntry.getExplanation();
	if (StringUtil.isNullString(explanation)) {
	  explanation = statusEntry.getStatus();
	}

	results.add(new ContentConfigurationResult(auId, statusEntry.getName(),
	  Boolean.FALSE, explanation));
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "results = " + results);
    return results;
  }
}
