/*
 * $Id: IdentityManager.java,v 1.62 2005-08-03 22:38:28 thib_gc Exp $
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.protocol;

import java.io.*;
import java.net.UnknownHostException;
import java.util.*;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.app.LockssAppException;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.poller.Poll;
import org.lockss.poller.Vote;
import org.lockss.state.HistoryRepository;
import org.lockss.util.*;

import sun.security.action.GetIntegerAction;

/**
 * Abstraction for identity of a LOCKSS cache.  Currently wraps an IP address.
 * @author Claire Griffin
 * @version 1.0
 */
public class IdentityManager
  extends BaseLockssDaemonManager implements ConfigurableManager {

  protected static Logger log = Logger.getLogger("IdentityManager");

  public static final String PARAM_LOCAL_IP =
    Configuration.PREFIX + "localIPAddress";

  /** The tcp port for the local V3 identity (at
   * org.lockss.localIPAddress).  Can be overridden by
   * org.lockss.localV3Identity */
  public static final String PARAM_LOCAL_V3_PORT =
    Configuration.PREFIX + "localV3Port";

  /** Local V3 identity string.  If this is set it will take precedence
   * over org.lockss.localV3Port */
  public static final String PARAM_LOCAL_V3_IDENTITY =
    Configuration.PREFIX + "localV3Identity";

  /** If true, restored agreement maps will be merged with any
   * alread-loaded map */
  public static final String PARAM_MERGE_RESTORED_AGREE_MAP =
    Configuration.PREFIX + "id.mergeAgreeMap";
  public static final boolean DEFAULT_MERGE_RESTORED_AGREE_MAP = true;


  static final String PREFIX = Configuration.PREFIX + "id.";
  static final String PARAM_MAX_DELTA = PREFIX + "maxReputationDelta";
  static final int DEFAULT_MAX_DELTA = 100;

  static final String PARAM_AGREE_DELTA = PREFIX + "agreeDelta";
  static final int DEFAULT_AGREE_DELTA = 100;

  static final String PARAM_DISAGREE_DELTA = PREFIX + "disagreeDelta";
  static final int DEFAULT_DISAGREE_DELTA = -150;

  static final String PARAM_CALL_INTERNAL = PREFIX + "callInternalDelta";
  static final int DEFAULT_CALL_INTERNAL = 100;

  static final String PARAM_SPOOF_DETECTED = PREFIX + "spoofDetected"; //no
  static final int DEFAULT_SPOOF_DETECTED = -30;

  static final String PARAM_REPLAY_DETECTED = PREFIX + "replayDetected";
  static final int DEFAULT_REPLAY_DETECTED = -20;

  static final String PARAM_ATTACK_DETECTED = PREFIX + "attackDetected"; //no
  static final int DEFAULT_ATTACK_DETECTED = -500;

  static final String PARAM_VOTE_NOTVERIFIED = PREFIX + "voteNotVerified ";
  static final int DEFAULT_VOTE_NOTVERIFIED = -30;

  static final String PARAM_VOTE_VERIFIED = PREFIX + "voteVerified";
  static final int DEFAULT_VOTE_VERIFIED = 40;

  static final String PARAM_VOTE_DISOWNED = PREFIX + "voteDisowned";
  static final int DEFAULT_VOTE_DISOWNED = -400;

  public static final String PARAM_IDDB_DIR = PREFIX + "database.dir";

  public static final String IDDB_FILENAME = "iddb.xml";
  // fully qualify for XmlMarshaller
  public static final String MAPPING_FILE_NAME =
    "/org/lockss/protocol/idmapping.xml";

  static final String V3_ID_SEPARATOR = ";";
  static final char V3_ID_SEPARATOR_CHAR = ';';

  /* Reputation constants */
  public static final int MAX_DELTA = 0;
  public static final int AGREE_VOTE = 1;
  public static final int DISAGREE_VOTE = 2;
  public static final int CALL_INTERNAL = 3;
  public static final int SPOOF_DETECTED = 4;
  public static final int REPLAY_DETECTED = 5;
  public static final int ATTACK_DETECTED = 6;
  public static final int VOTE_NOTVERIFIED = 7;
  public static final int VOTE_VERIFIED = 8;
  public static final int VOTE_DISOWNED = 9;

  static final int INITIAL_REPUTATION = 500;
  static final int REPUTATION_NUMERATOR = 1000;

  static LockssRandom theRandom = new LockssRandom();

  /*
   * There are two classes representing the identity of a peer.
   * PeerData is used only by the IdentityManager; instances
   * contain all the actual information about the peer.  PeerIdentity
   * is an opaque cookie that the IdentityManager provides to its
   * clients; they use it to refer to a peer without having a reference
   * to an instance of PeerData.  Peers have very long lives and
   * potentially have a lot of information but only a few will be
   * actively in use at any one time. The PeerData
   * is found by looking up the PeerIdentity in a HashMap called
   * thePeerCache;  it should be a cache of active PeerData
   * instances but it currently contains all of them.
   * XXX currently using LcapIdentity instead of PeerData
   */

  // IP address of our identity (not necessarily this machine's IP if
  // behind NAT).  (All current identities are IP-based; future ones may
  // not be.)
  protected IPAddr theLocalIPAddr = null;
  // Array of PeerIdentity for each of our local identities, (potentially)
  // one per protocol version.
  protected PeerIdentity localPeerIdentities[];

  // both maps keyed by identity key string
  private Map theIdentities;		// all known identities (keys are
					// PeerIdentitys)
  private Map thePeerIdentities;   // all PeerIdentities (keys are strings)

  File iddbFile = null;

  int[] reputationDeltas = new int[10];

  private boolean isMergeRestoredAgreemMap = DEFAULT_MERGE_RESTORED_AGREE_MAP;

  // Maps au to its agreement map, which maps PeerIdentity -> IdentityAgreement
  private Map agreeMaps = new HashMap();;

  private IdentityManagerStatus status;



  public IdentityManager() { }

  public void initService(LockssDaemon daemon) throws LockssAppException {
    super.initService(daemon);
    // initializing these here makes testing more predictable
    setupLocalIdentities();
  }

  // This is protected only so it can be overridden in a mock subcless in
  // another package (TestRemoteApi).  Won't be necessary when there's an
  // interface for the mock class to implement instead
  protected void setupLocalIdentities() {
    localPeerIdentities = new PeerIdentity[Poll.MAX_POLL_VERSION+1];
    theIdentities = new HashMap();
    thePeerIdentities = new HashMap();

    // Create local PeerIdentity and LcapIdentity instances
    Configuration config = ConfigManager.getCurrentConfig();
    // Find local IP addr and create V1 identity
    String localV1IdentityStr = getLocalIpParam(config);
    if (localV1IdentityStr == null) {
      String msg = "Cannot start: " + PARAM_LOCAL_IP + " is not set";
      log.critical(msg);
      throw new LockssAppException("IdentityManager: " + msg);
    }
    try {
      theLocalIPAddr = IPAddr.getByName(localV1IdentityStr);
    } catch (UnknownHostException uhe) {
      String msg = "Cannot start: Can't lookup \"" + localV1IdentityStr + "\"";
      log.critical(msg);
      throw new LockssAppException("IdentityManager: " + msg);
    }
    try {
      localPeerIdentities[Poll.V1_POLL] =
	findLocalPeerIdentity(localV1IdentityStr);
    } catch (MalformedIdentityKeyException e) {
      String msg = "Cannot start: Can't create local identity:" +
	localV1IdentityStr;
      log.critical(msg, e);
      throw new LockssAppException("IdentityManager: " + msg);
    }
    // Create V3 identity if configured
    String v3idstr = config.get(PARAM_LOCAL_V3_IDENTITY);
    if (StringUtil.isNullString(v3idstr) &&
	config.containsKey(PARAM_LOCAL_V3_PORT)) {
      int localV3Port = config.getInt(PARAM_LOCAL_V3_PORT, -1);
      if (localV3Port > 0) {
	v3idstr = ipAddrToKey(localV1IdentityStr, localV3Port);
      }
    }
    if (v3idstr != null) {
      try {
	localPeerIdentities[Poll.V3_POLL] = findLocalPeerIdentity(v3idstr);
      } catch (MalformedIdentityKeyException e) {
	String msg = "Cannot start: Can't create local V3 identity: " +
	  v3idstr;
	log.critical(msg, e);
	throw new LockssAppException("IdentityManager: " + msg);
      }
//     } else {
//       log.debug("No V3 identity created");
    }
  }

  /**
   * start the identity manager.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();
    reloadIdentities();

    log.info("Local V1 identity: " + getLocalPeerIdentity(Poll.V1_POLL));
    if (localPeerIdentities[Poll.V3_POLL] != null) {
      log.info("Local V3 identity: " + getLocalPeerIdentity(Poll.V3_POLL));
    }
    status = makeStatusAccessor(theIdentities);
    getDaemon().getStatusService().registerStatusAccessor("Identities",
							  status);

    Vote.setIdentityManager(this);
    LcapMessage.setIdentityManager(this);
  }

  protected IdentityManagerStatus makeStatusAccessor(Map theIdentities) {
    return new IdentityManagerStatus(theIdentities);
  }

  /**
   * stop the identity manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    try {
      storeIdentities();
    }
    catch (ProtocolException ex) {
    }
    super.stopService();
    Vote.setIdentityManager(null);
    LcapMessage.setIdentityManager(null);
  }

  /** Find or create unique instances of both PeerIdentity and LcapIdentity.
      (Eventually, LcapIdentity won't always be created here.) */
  private PeerIdentity findLocalPeerIdentity(String key)
      throws MalformedIdentityKeyException {
    PeerIdentity pid;
    synchronized (thePeerIdentities) {
      pid = (PeerIdentity)thePeerIdentities.get(key);
      if (pid == null) {
	pid = new PeerIdentity.LocalIdentity(key);
	thePeerIdentities.put(key, pid);
      }
    }
    // for now always make sure LcapIdentity instance exists
    findLcapIdentity(pid, key);
    return pid;
  }

  /** Find or create unique instance of PeerIdentity */
  public PeerIdentity findPeerIdentity(String key) {
    synchronized (thePeerIdentities) {
      PeerIdentity pid = (PeerIdentity)thePeerIdentities.get(key);
      if (pid == null) {
	pid = new PeerIdentity(key);
	thePeerIdentities.put(key, pid);
      }
      return pid;
    }
  }

  /** Find or create unique instances of both PeerIdentity and LcapIdentity.
      (Eventually, LcapIdentity won't always be created here.) */
  private PeerIdentity findPeerIdentityAndData(String key)
      throws MalformedIdentityKeyException {
    PeerIdentity pid = findPeerIdentity(key);
    // for now always make sure LcapIdentity instance exists
    findLcapIdentity(pid, key);
    return pid;
  }

  /** Find or create unique instances of both PeerIdentity and LcapIdentity.
      (Eventually, LcapIdentity won't always be created here.) */
  private PeerIdentity findPeerIdentityAndData(IPAddr addr, int port) {
    String key = ipAddrToKey(addr, port);
    PeerIdentity pid = findPeerIdentity(key);
    // for now always make sure LcapIdentity instance exists
    findLcapIdentity(pid, addr, port);
    return pid;
  }

  /** Find or create unique instance of LcapIdentity. */
  protected LcapIdentity findLcapIdentity(PeerIdentity pid, String key)
      throws MalformedIdentityKeyException {
    synchronized (theIdentities) {
      LcapIdentity lid = (LcapIdentity)theIdentities.get(pid);
      if (lid == null) {
	theIdentities.put(pid, new LcapIdentity(pid, key));
      }
      return lid;
    }
  }

  /** Find or create unique instance of LcapIdentity. */
  protected LcapIdentity findLcapIdentity(PeerIdentity pid,
					  IPAddr addr, int port) {
    synchronized (theIdentities) {
      LcapIdentity lid = (LcapIdentity)theIdentities.get(pid);
      if (lid == null) {
	theIdentities.put(pid, new LcapIdentity(pid, addr, port));
      }
      return lid;
    }
  }

  public static String ipAddrToKey(String addr, int port) {
    return ((port == 0)
	    ? addr
	    : addr + V3_ID_SEPARATOR + String.valueOf(port));
  }

  public static String ipAddrToKey(IPAddr addr, int port) {
    return ipAddrToKey(addr.getHostAddress(), port);
  }

  /**
   * ipAddrToPeerIdentity returns the peer identity matching the
   * IP address and port.  An instance is created if necesary.
   * Used only by LcapDatagramRouter (and soon by its stream analog).
   * @param addr the IPAddr of the peer, null for the local peer
   * @param port the port of the peer
   * @return the PeerIdentity representing the peer
   */
  public PeerIdentity ipAddrToPeerIdentity(IPAddr addr, int port) {
    if (addr == null) {
      log.warning("ipAddrToPeerIdentity(null) is deprecated.");
      log.warning("  Use getLocalPeerIdentity() to get a local identity");
      // XXX return V1 identity until all callers fixed
      return localPeerIdentities[Poll.V1_POLL];
    } else {
      return findPeerIdentityAndData(addr, port);
    }
  }

  public PeerIdentity ipAddrToPeerIdentity(IPAddr addr) {
    return ipAddrToPeerIdentity(addr, 0);
  }

  /**
   * stringToPeerIdentity returns the peer identity matching the
   * String ip address and port.  An instance is created if necesary.
   * Used only by LcapMessage (and soon by its stream analog).
   * @param idKey the ip addr and port of the peer, null for the local peer
   * @return the PeerIdentity representing the peer
   */
  public PeerIdentity stringToPeerIdentity(String idKey)
      throws IdentityManager.MalformedIdentityKeyException {
    if (idKey == null) {
      log.warning("stringToPeerIdentity(null) is deprecated.");
      log.warning("  Use getLocalPeerIdentity() to get a local identity");
      // XXX return V1 identity until all callers fixed
      return localPeerIdentities[Poll.V1_POLL];
    } else {
      return findPeerIdentityAndData(idKey);
    }
  }

  public IPAddr identityToIPAddr(PeerIdentity pid) {
    LcapIdentity lid = (LcapIdentity)theIdentities.get(pid);
    if (lid == null) {
      log.error(pid.toString() + " has no LcapIdentity");
    } else if (lid.getPort() != 0) {
      log.error(pid.toString() + " is not a V1 identity");
    } else {
      return lid.getAddress();
    }
    throw new IllegalArgumentException(pid.toString());
  }

  /**
   * getLocalPeerIdentity returns the local peer identity
   * @param pollVersion the poll protocol version
   * @return the local peer identity associated with the poll version
   * @throws IllegalArgumentException if the pollVersion is not configured
   * or is outside the legal range
   */
  public PeerIdentity getLocalPeerIdentity(int pollVersion) {
    PeerIdentity pid = null;
    try {
      pid = localPeerIdentities[pollVersion];
    } catch (ArrayIndexOutOfBoundsException e) {
      // fall through
    }
    if (pid == null) {
      throw new IllegalArgumentException("Illegal poll version: " +
					 pollVersion);
    }
    return pid;
  }

  /**
   * getLocalIPAddr returns the IPAddr of the local peer
   * @return the IPAddr of the local peer
   */
  public IPAddr getLocalIPAddr() {
    return theLocalIPAddr;
  }

  /**
   * return true if this PeerIdentity is the same as the local host
   * @param id the PeerIdentity
   * @return boolean true if is the local identity, false otherwise
   */
  public boolean isLocalIdentity(PeerIdentity id) {
    return id.isLocalIdentity();
  }

  /**
   * return true if this PeerIdentity is the same as the local host
   * @param idStr the string representation of the voter's PeerIdentity
   * @return boolean true if is the local identity, false otherwise
   */
  public boolean isLocalIdentity(String idStr) {
    try {
      return isLocalIdentity(stringToPeerIdentity(idStr));
    } catch (IdentityManager.MalformedIdentityKeyException e) {
      return false;
    }
  }

  /**
   * rememberEvent associates the event with the peer identity
   * @param id the PeerIdentity
   * @param event the event code
   * @param msg the LcapMessage involved
   */
  public void rememberEvent(PeerIdentity id, int event, LcapMessage msg) {
    LcapIdentity lid = (LcapIdentity)theIdentities.get(id);
    if (lid != null) {
      lid.rememberEvent(event, msg);
    }
  }
  /**
   * return the max value of an Identity's reputation
   * @return the int value of max reputation
   */
  public int getMaxReputation() {
    return REPUTATION_NUMERATOR;
  }

  /**
   * getReputation returns the reputation of the peer
   * @param id the PeerIdentity
   * @return the reputation
   */
  public int getReputation(PeerIdentity id) {
    int ret = 0;
    LcapIdentity lid = (LcapIdentity)theIdentities.get(id);
    if (lid == null) {
      log.error("Can't find LcapIdentity for " + id.toString());
    } else {
      ret = lid.getReputation();
    }
    return ret;
  }

  /**
   * getReputationDelta returns the amount of reputation change that
   * reflects the specified kind of event.
   * @param changeKind the type of event
   * @return the delta that would be applied to a peer's reputation
   */
  protected int getReputationDelta(int changeKind) {
    int ret = -1;
    if (changeKind >= 0 && changeKind < reputationDeltas.length)
      ret = reputationDeltas[changeKind];
    return ret;
  }

  /**
   * changeReputation makes the change to the reputation of the peer "id"
   * matching the event "changeKind"
   * @param id the PeerIdentity of the peer to affect
   * @param changeKind the type of event that is being reflected
   */
  public void changeReputation(PeerIdentity id, int changeKind) {
    int delta = getReputationDelta(changeKind);
    int max_delta = reputationDeltas[MAX_DELTA];
    LcapIdentity lid = (LcapIdentity)theIdentities.get(id);
    if (lid == null) {
      log.error("Can't find LcapIdentity for " + id.toString());
      return;
    }
    int reputation = lid.getReputation();

    if (id.isLocalIdentity()) {
      log.debug(id.toString() + " ignoring reputation delta " + delta);
      return;
    }

    delta = (int) (((float) delta) * theRandom.nextFloat());

    if (delta > 0) {
      if (delta > max_delta) {
	delta = max_delta;
      }
      if (delta > (REPUTATION_NUMERATOR - reputation)) {
	delta = (REPUTATION_NUMERATOR - reputation);
      }
    } else if (delta < 0) {
      if (delta < (-max_delta)) {
	delta = -max_delta;
      }
      if ((reputation + delta) < 0) {
	delta = -reputation;
      }
    }
    if (delta != 0)
      log.debug(id.toString() +" change reputation from " + reputation +
		" to " + (reputation + delta));
    lid.changeReputation(delta);
  }


  File setupIddbFile() {
    if (iddbFile == null) {
      String iddbDir = Configuration.getParam(PARAM_IDDB_DIR);
      if (iddbDir != null) {
	iddbFile = new File(iddbDir, IDDB_FILENAME);
      }
    }
    return iddbFile;
  }

  /** Reload the peer data from the identity database.  This may overwrite
   * the LcapIdentity instance for local identity(s).  That may not be
   * appropriate if this is ever called other than at startup. */
  private void reloadIdentities() {
    reloadIdentities(makeIdentityListSerializer());
  }
  
  private void reloadIdentities(ObjectSerializer deserializer) {
    if (setupIddbFile() == null) {
      log.warning("Cannot load identities; no value for '"
          + PARAM_IDDB_DIR + "'.");
      return;
    }

    synchronized (iddbFile) {
      try {
        // CASTOR: Remove unwrap() call; add cast to HashMap
        HashMap map = unwrap(deserializer.deserialize(iddbFile));
        synchronized (theIdentities) {
          theIdentities.putAll(map);
        }
      }
      catch (Exception e) {
        log.warning("Could not load identity database: " + e.getMessage());
      }
    }
  }

  /**
   * storeIdentities is used by the PollManager to record the result of
   * tallying a poll.
   */
  public void storeIdentities()
      throws ProtocolException {
    storeIdentities(makeIdentityListSerializer());
  }

  /**
   * storeIdentities is used by the PollManager to record the result of
   * tallying a poll.
   */
  public void storeIdentities(ObjectSerializer serializer)
      throws ProtocolException {
    if (setupIddbFile() == null) {
      log.warning("Cannot store identities; no value for '"
          + PARAM_IDDB_DIR + "'.");
      return;
    }
    
    synchronized (iddbFile) {
      try {
        File dir = iddbFile.getParentFile();
        if (dir != null && !dir.exists()) { dir.mkdirs(); }
        serializer.serialize(iddbFile, wrap(theIdentities));
      }
      catch (Exception e) {
        log.error("Could not store identity database", e);
        throw new ProtocolException("Unable to store identity database.");
      }
    }
  }

  private static ObjectSerializer makeIdentityListSerializer() {
    // CASTOR: Change to returning an XStreamSerializer
    CXSerializer serializer =
      new CXSerializer(MAPPING_FILE_NAME, IdentityListBean.class);
    serializer.setCurrentMode(getSerializationMode());
    return serializer;
  }
  
  /** Copy the identity db file to the stream.
   */
  // XXX hokey way to have the acceess performed by the object that has the
  // appropriate lock
  public void writeIdentityDbTo(OutputStream out) throws IOException {
    if (setupIddbFile() == null) {
      return;
    }
    if (iddbFile.exists()) {
      synchronized (iddbFile) {
	InputStream in =
	  new BufferedInputStream(new FileInputStream(iddbFile));
	try {
	  StreamUtil.copy(in, out);
	} finally {
	  IOUtil.safeClose(in);
	}
      }
    }
  }

  /**
   * <p>Retrieves the current serialization mode.</p>
   * @return A mode constant from {@link CXSerializer}.
   */
  private static int getSerializationMode() {
    return CXSerializer.getModeFromConfiguration();
  }
  
  public IdentityListBean getIdentityListBean() {
    // CASTOR: This method should disappear with Castor
    synchronized(theIdentities) {
      List beanList = new ArrayList(theIdentities.size());
      Iterator mapIter = theIdentities.values().iterator();
      while(mapIter.hasNext()) {
        LcapIdentity id = (LcapIdentity) mapIter.next();
        IdentityBean bean = new IdentityBean(id.getIdKey(),id.getReputation());
        beanList.add(bean);
      }
      IdentityListBean listBean = new IdentityListBean(beanList);
      return listBean;
    }
  }
  
  private Object wrap(Map theIdentities) {
    // CASTOR: This method disappears with Castor
    if (getSerializationMode() == CXSerializer.CASTOR_MODE) {
      return getIdentityListBean();
    }
    else {
      return theIdentities;
    }
  }
  
  private HashMap unwrap(Object obj) {
    if (obj instanceof IdentityListBean) {
      HashMap map = new HashMap();
      // JAVA5: Use foreach
      Iterator beanIter = ((IdentityListBean)obj).getIdBeans().iterator();
      while (beanIter.hasNext()) {
        IdentityBean bean = (IdentityBean)beanIter.next();
        String idKey = bean.getKey();
        try {
          PeerIdentity pid = findPeerIdentity(idKey);
          LcapIdentity id = new LcapIdentity(pid, idKey, bean.getReputation());
          map.put(pid, id);
        }
        catch (MalformedIdentityKeyException ex) {
          log.warning("Error reloading identity - Unknown host: " + idKey);
        }
      }
      return map;
    }
    else {
      return (HashMap)obj;
    }
  }

  /**
   * Signals that we've agreed with id on a top level poll on au.
   * Only called if we're both on the winning side
   * @param pid the PeerIdentity of the agreeing peer
   * @param au the {@link ArchivalUnit}
   */
  public void signalAgreed(PeerIdentity pid, ArchivalUnit au) {
    signalAgreed(pid, au, TimeBase.nowMs());
  }

  private void signalAgreed(PeerIdentity pid, ArchivalUnit au, long time) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    } else if (pid == null) {
      throw new IllegalArgumentException("Called with null pid");
    }
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      IdentityAgreement ida = findPeerIdentityAgreement(map, pid);
      if (time > ida.getLastAgree()) {
	ida.setLastAgree(time);
	storeIdentityAgreement(au);
      }
    }
  }

  /**
   * Signals that we've disagreed with id on any level poll on au.
   * Only called if we're on the winning side
   * @param pid the PeerIdentity of the disagreeing peer
   * @param au the {@link ArchivalUnit}
   */
  public void signalDisagreed(PeerIdentity pid, ArchivalUnit au) {
    signalDisagreed(pid, au, TimeBase.nowMs());
  }

  private void signalDisagreed(PeerIdentity pid, ArchivalUnit au, long time) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    } else if (pid == null) {
      throw new IllegalArgumentException("Called with null pid");
    }
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      IdentityAgreement ida = findPeerIdentityAgreement(map, pid);
      if (time > ida.getLastDisagree()) {
	ida.setLastDisagree(time);
	storeIdentityAgreement(au);
      }
    }
  }

  /**
   * @param au ArchivalUnit to look up PeerIdentities for
   * @return List of peers from which to try to fetch repairs for the AU.
   * Peers with whom we have had any disagreement since the last toplevel
   * agreement are placed at the end of the list.
   */
  public List getCachesToRepairFrom(ArchivalUnit au) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    }
    Map map = findAuAgreeMap(au);
    List res = new LinkedList();
    synchronized (map) {
      for (Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
	Map.Entry ent = (Map.Entry)it.next();
	IdentityAgreement ida = (IdentityAgreement)ent.getValue();
	if (ida.hasAgreed()) {
	  if (ida.getLastDisagree() > ida.getLastAgree()) {
	    res.add(ent.getKey());
	  } else {
	    res.add(0, ent.getKey());
	  }
	}
      }
    }
    return res;
  }

  public boolean hasAgreed(String ip, ArchivalUnit au)
      throws MalformedIdentityKeyException {
    return hasAgreed(stringToPeerIdentity(ip), au);
  }

  public boolean hasAgreed(PeerIdentity pid, ArchivalUnit au) {
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      IdentityAgreement ida = (IdentityAgreement)map.get(pid);
      return ida != null ? ida.hasAgreed() : false;
    }
  }

  /** Return collection of IdentityManager.IdentityAgreement for each peer
   * that we have a record of agreeing or disagreeing with us */
  public Collection getIdentityAgreements(ArchivalUnit au) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    }
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      return new ArrayList(map.values());
    }
  }

  /** Return map peer -> last agree time.  Used for logging and debugging */
  public Map getAgreed(ArchivalUnit au) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    }
    Map map = findAuAgreeMap(au);
    Map res = new HashMap();
    synchronized (map) {
      for (Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
	Map.Entry ent = (Map.Entry)it.next();
	IdentityAgreement ida = (IdentityAgreement)ent.getValue();
	if (ida.hasAgreed()) {
	  res.put(ent.getKey(), new Long(ida.getLastAgree()));
	}
      }
    }
    return res;
  }

  /** Return map peer -> last disagree time.  Used for logging and debugging */
  public Map getDisagreed(ArchivalUnit au) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    }
    Map map = findAuAgreeMap(au);
    Map res = new HashMap();
    synchronized (map) {
      for (Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
	Map.Entry ent = (Map.Entry)it.next();
	IdentityAgreement ida = (IdentityAgreement)ent.getValue();
	if (ida.getLastDisagree() != 0) {
	  res.put(ent.getKey(), new Long(ida.getLastDisagree()));
	}
      }
    }
    return res;
  }

  // called in synchronized block
  private IdentityAgreement findPeerIdentityAgreement(Map map,
						      PeerIdentity pid) {
    IdentityAgreement ida = (IdentityAgreement)map.get(pid);
    if (ida == null) {
      ida = new IdentityAgreement(pid);
      map.put(pid, ida);
    }
    return ida;
  }

  static String AGREE_MAP_INIT_KEY = "needs_init";

  Map findAuAgreeMap(ArchivalUnit au) {
    Map map;
    synchronized (agreeMaps) {
      map = (Map)agreeMaps.get(au.getAuId());
      if (map == null) {
	map = new HashMap();
	map.put(AGREE_MAP_INIT_KEY, "true");
	agreeMaps.put(au.getAuId(), map);
      }
    }
    synchronized (map) {
      if (map.containsKey(AGREE_MAP_INIT_KEY)) {
	loadIdentityAgreement(map, au);
	map.remove(AGREE_MAP_INIT_KEY);
      }
    }
    return map;
  }

  public boolean hasAgreeMap(ArchivalUnit au) {
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      return !map.isEmpty();
    }
  }

  /** Copy the identity agreement file for the AU to the stream.
   */
  // XXX hokey way to have the acceess performed by the object that has the
  // appropriate lock
  public void writeIdentityAgreementTo(ArchivalUnit au, OutputStream out)
      throws IOException {
    HistoryRepository hRep = getDaemon().getHistoryRepository(au);
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      File file = hRep.getIdentityAgreementFile();
      InputStream in = new BufferedInputStream(new FileInputStream(file));
      try {
	StreamUtil.copy(in, out);
      } finally {
	IOUtil.safeClose(in);
      }
    }
  }

  /** Install the contents of the stream as the identity agreement file for
   * the AU
   */
  // XXX hokey way to have the acceess performed by the object that has the
  // appropriate lock
  public void readIdentityAgreementFrom(ArchivalUnit au, InputStream in)
      throws IOException {
    HistoryRepository hRep = getDaemon().getHistoryRepository(au);
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      File file = hRep.getIdentityAgreementFile();
      OutputStream out = new FileOutputStream(file);
      try {
	StreamUtil.copy(in, out);
      } finally {
	IOUtil.safeClose(out);
	map.put(AGREE_MAP_INIT_KEY, "true");	// ensure map is reread
      }
    }
  }

  //only called within a synchronized block, so we don't need to
  private Map loadIdentityAgreement(Map map, ArchivalUnit au) { 
   HistoryRepository hRep = getDaemon().getHistoryRepository(au);
    List list = hRep.loadIdentityAgreements();
    if (map == null) {
      map = new HashMap();
    }
    if (list == null) {
      if (!isMergeRestoredAgreemMap) {
	map.clear();
      }
    } else {
      Set prevOnlyPids = new HashSet(map.keySet());
      for (Iterator it = list.iterator(); it.hasNext(); ) {
	IdentityAgreement ida = (IdentityAgreement)it.next();
	try {
	  PeerIdentity pid = stringToPeerIdentity(ida.getId());
	  if (isMergeRestoredAgreemMap) {
	    ida = mergeIdAgreement((IdentityAgreement)map.get(pid), ida);
	  } else {
	    prevOnlyPids.remove(pid);
	  }
	  map.put(pid, ida);
	} catch (MalformedIdentityKeyException e) {
	  log.warning("Couldn't load agreement for key " + ida.getId(), e);
	}
      }
      if (!isMergeRestoredAgreemMap) {
	for (Iterator it = prevOnlyPids.iterator(); it.hasNext(); ) {
	  Object pid = it.next();
	  if (pid instanceof PeerIdentity) {
	    map.remove(pid);
	  }
	}
      }
    }
    return map;
  }

  private void storeIdentityAgreement(ArchivalUnit au) {
    HistoryRepository hRep = getDaemon().getHistoryRepository(au);
    Map map = findAuAgreeMap(au);
    synchronized (map) {
      hRep.storeIdentityAgreements(new ArrayList(map.values()));
    }
  }

  private IdentityAgreement mergeIdAgreement(IdentityAgreement prev,
					     IdentityAgreement ida) {
    if (prev == null) {
      return ida;
    }
    prev.mergeFrom(ida);
    return prev;
  }

  public void setConfig(Configuration config, Configuration oldConfig,
			Configuration.Differences changedKeys) {
    reputationDeltas[MAX_DELTA] =
      config.getInt(PARAM_MAX_DELTA, DEFAULT_MAX_DELTA);
    reputationDeltas[AGREE_VOTE] =
      config.getInt(PARAM_AGREE_DELTA, DEFAULT_AGREE_DELTA);
    reputationDeltas[DISAGREE_VOTE] =
      config.getInt(PARAM_DISAGREE_DELTA, DEFAULT_DISAGREE_DELTA);
    reputationDeltas[CALL_INTERNAL] =
      config.getInt(PARAM_CALL_INTERNAL, DEFAULT_CALL_INTERNAL);
    reputationDeltas[SPOOF_DETECTED] =
      config.getInt(PARAM_SPOOF_DETECTED, DEFAULT_SPOOF_DETECTED);
    reputationDeltas[REPLAY_DETECTED] =
      config.getInt(PARAM_REPLAY_DETECTED, DEFAULT_REPLAY_DETECTED);
    reputationDeltas[ATTACK_DETECTED] =
      config.getInt(PARAM_ATTACK_DETECTED, DEFAULT_ATTACK_DETECTED);
    reputationDeltas[VOTE_NOTVERIFIED] =
      config.getInt(PARAM_VOTE_NOTVERIFIED, DEFAULT_VOTE_NOTVERIFIED);
    reputationDeltas[VOTE_VERIFIED] =
      config.getInt(PARAM_VOTE_VERIFIED, DEFAULT_VOTE_VERIFIED);
    reputationDeltas[VOTE_DISOWNED] =
      config.getInt(PARAM_VOTE_DISOWNED, DEFAULT_VOTE_DISOWNED);

    isMergeRestoredAgreemMap =
      config.getBoolean(PARAM_MERGE_RESTORED_AGREE_MAP,
			DEFAULT_MERGE_RESTORED_AGREE_MAP);
  }


  public static class IdentityAgreement {
    private long lastAgree = 0;
    private long lastDisagree = 0;
    private String id = null;

    public IdentityAgreement(PeerIdentity pid) {
      this.id = pid.getIdString();
    }

    // needed for marshalling
    public IdentityAgreement() {}

    public long getLastAgree() {
      return lastAgree;
    }

    public void setLastAgree(long lastAgree) {
      this.lastAgree = lastAgree;
    }

    public long getLastDisagree() {
      return lastDisagree;
    }

    public void setLastDisagree(long lastDisagree) {
      this.lastDisagree = lastDisagree;
    }

    public String getId() {
      return id;
    }

    public boolean hasAgreed() {
      return lastAgree != 0;
    }

    public void setId(String id) {
      this.id = id;
    }

    public void mergeFrom(IdentityAgreement ida) {
      long ag = ida.getLastAgree();
      if (ag > getLastAgree()) {
	setLastAgree(ag);
      }
      long dis = ida.getLastDisagree();
      if (dis > getLastDisagree()) {
	setLastDisagree(dis);
      }
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("[IdentityAgreement: ");
      sb.append("id=");
      sb.append(id);
      sb.append(", ");
      sb.append("lastAgree=");
      sb.append(lastAgree);
      sb.append(", ");
      sb.append("lastDisagree=");
      sb.append(lastDisagree);
      sb.append("]");
      return sb.toString();
    }

    public boolean equals(Object obj) {
      if (obj instanceof IdentityAgreement) {
	IdentityAgreement ida = (IdentityAgreement)obj;
	return (id.equals(ida.getId())
		&& ida.getLastDisagree() == lastDisagree
		&& ida.getLastAgree() == lastAgree);
      }
      return false;
    }

    public int hashCode() {
      return 7 * id.hashCode() + 3 * (int)(getLastDisagree() + getLastAgree());
    }
  }

  /** Exception thrown for illegal identity keys. */
  public static class MalformedIdentityKeyException extends IOException {
    public MalformedIdentityKeyException(String message) {
      super(message);
    }
  }

  /** overridable for testing */
  protected String getLocalIpParam(Configuration config) {
    return config.get(PARAM_LOCAL_IP);
  }
}
