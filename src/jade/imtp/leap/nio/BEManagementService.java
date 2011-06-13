/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/
package jade.imtp.leap.nio;

//#J2ME_EXCLUDE_FILE
import jade.core.*;
import jade.imtp.leap.ICPException;
import jade.util.Logger;
import jade.util.leap.Properties;
import jade.imtp.leap.TransportProtocol;
import jade.imtp.leap.FrontEndStub;
import jade.imtp.leap.JICP.*;
import jade.security.JADESecurityException;

import java.io.*;
import java.nio.channels.*;
import java.net.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Level;

/**
This service handles BEContainer creation requests and manages IO operations
for data exchanged between the created BEContainers and their FEContainers
using java.nio
<br><br>
Having this functionality implemented as a service allows propagating
(through the ServiceHelper) BackEnd related events (e.g. disconnections and 
reconnections) at the agent level.
<br><br>
This service accepts the following configuration parameters:<br>
<code>jade_imtp_leap_nio_BEManagementService_servers</code>: list of of IOEventServer ids separated by ';'<br>
Actually this service is a collection of IOEventServer objects.
Each IOEventServer opens and manages a server socket, accepts 
BackEnd creation/connection requests and passes incoming data to BEDispatchers
independently from the others.
If the above parameter is not specified a single IOEventServer will
be started and its ID will be <code>jade_imtp_leap_nio_BEManagementService</code>.
All other parameters are related to a single IOEventServer and must 
be specified in the form<br> 
serverid_parametername<br>
They are:
<ul>
<li>
<code>serverid_local-host</code>: Specifies the local network interface 
for the server socket managed by this server (defaults to localhost).
</li>
<li>
<code>serverid_local-port</code>: Specifies the local port for the server 
socket managed by this server (defaults to 2099)
</li>
<li>
<code>serverid_protocol</code>: Specifies the protocol used by this 
server in the form of <code>jade.imtp.leap.JICP.ProtocolManager<code> 
class
</li>
<li>
<code>serverid_leap-property-file</code>: Specifies the leap-property
file to be used by this server.
</li>
<li>
<ode>serverid_poolsize</code>: Specifies the number of threads used by 
this server to manage IO events.
</li>
<ul>

@author Giovanni Caire - TILAB
 */
public class BEManagementService extends BaseService {

	public static final String NAME = "BEManagement";

	private static final String PREFIX = "jade_imtp_leap_nio_BEManagementService_";

	public static final String ACCEPT = PREFIX+"accept";
	public static final String SERVERS = PREFIX+"servers";

	private static final int DEFAULT_PORT = 2099;
	private static final int DEFAULT_POOL_SIZE = 5;
	private static final int INIT_STATE = 0;
	private static final int ACTIVE_STATE = 1;
	private static final int TERMINATING_STATE = 2;
	private static final int TERMINATED_STATE = 3;
	private static final int ERROR_STATE = -1;
	public static final String INCOMING_CONNECTION = "Incoming-Connection";

	private static final String[] OWNED_COMMANDS = new String[]{
		INCOMING_CONNECTION
	};

	private static final Map<String, Class> protocolManagers = new HashMap<String, Class>();
	static {
		protocolManagers.put(MicroRuntime.SOCKET_PROTOCOL, NIOJICPPeer.class);
		protocolManagers.put(MicroRuntime.SSL_PROTOCOL, NIOJICPSPeer.class);
		protocolManagers.put(MicroRuntime.HTTP_PROTOCOL, NIOHTTPPeer.class);
		protocolManagers.put(MicroRuntime.HTTPS_PROTOCOL, NIOHTTPSPeer.class);
	}

	private Hashtable servers = new Hashtable(2);
	private Ticker myTicker;
	private ServiceHelper myHelper;
	private String platformName;
	// The list of addresses considered malicious. Connections from
	// these addresses will be rejected.
	// FIXME: The mechanism for filling/clearing this list is not yet
	// defined/implemented
	private Vector maliciousAddresses = new Vector();
	private Logger myLogger = Logger.getMyLogger(getClass().getName());

	/**
    @return The name of this service.
	 */
	public String getName() {
		String className = getClass().getName();
		return className.substring(0, className.indexOf("Service"));
	}

	public String[] getOwnedCommands() {
		return OWNED_COMMANDS;
	}

	public void init(AgentContainer ac, Profile p) throws ProfileException {
		super.init(ac, p);

		platformName = ac.getPlatformID();

		// Initialize the BE-Manager
		BackEndManager.getInstance(p);

		handleAcceptOption(p);
	}

	private void handleAcceptOption(Profile p) throws ProfileException {
		// Manage the shortcut configuration option jade_imtp_leap_nio_BEManagementService_accept
		String acceptOption = p.getParameter(ACCEPT, null);
		if (acceptOption != null) {
			// The jade_imtp_leap_nio_BEManagementService_accept option has a form of type
			// socket;http(8080)
			// This means that we will accepts plain sockets on the default port and HTTP on port 8080
			jade.util.leap.List acceptedProtocols = p.getSpecifiers(ACCEPT);
			// Do not overwrite explicitly specified servers if any
			String serverIDs = p.getParameter(SERVERS, null);
			serverIDs = (serverIDs != null ? serverIDs += ';' : "");
			for (int i = 0; i < acceptedProtocols.size(); ++i) {
				Specifier s = (Specifier) acceptedProtocols.get(i);
				String proto = s.getClassName();
				if (proto.equalsIgnoreCase(MicroRuntime.SOCKET_PROTOCOL) ||
						proto.equalsIgnoreCase(MicroRuntime.SSL_PROTOCOL) ||	
						proto.equalsIgnoreCase(MicroRuntime.HTTP_PROTOCOL) ||	
						proto.equalsIgnoreCase(MicroRuntime.HTTPS_PROTOCOL)) {	
					String semicolon = ((i+1) == acceptedProtocols.size() ? "" : ";");
					serverIDs = serverIDs + manageAcceptedProtocol(s, proto.toLowerCase(), p)+semicolon;
				}
				else {
					myLogger.log(Logger.WARNING, "Unsupported protocol "+proto+". Permitted values are socket, ssl, http and https!!!!");
				}
			}
			p.setParameter(SERVERS, serverIDs);
		}
	}

	private String manageAcceptedProtocol(Specifier s, String protocolName, Profile p) {
		String id = PREFIX+protocolName;
		p.setParameter(id + '_' + "protocol", protocolManagers.get(protocolName).getName());
		if (s.getArgs() != null && s.getArgs().length > 0) {
			p.setParameter(id + '_' + JICPProtocol.LOCAL_PORT_KEY, (String) s.getArgs()[0]);
		}
		return id;
	}

	/**
    This method is called by the JADE runtime just after this service
    has been installed.
    It activates the IO event servers specified in the Profile and the
    Ticker thread.
	 */
	public void boot(Profile p) throws ServiceException {
		// Get IDs of servers to install
		String defaultServerIDs = PREFIX.substring(0, PREFIX.length() - 1);
		String serverIDs = p.getParameter(SERVERS, defaultServerIDs);
		Vector v = Specifier.parseList(serverIDs, ';'); 

		// Activate all servers
		Enumeration e = v.elements();
		while (e.hasMoreElements()) {
			String id = (String) e.nextElement();
			try {
				IOEventServer srv = new IOEventServer();
				srv.init(id, p);
				servers.put(id, srv);
				srv.activate();
			} catch (Throwable t) {
				myLogger.log(Logger.WARNING, "Error activating IOEventServer " + id + ". " + t);
				t.printStackTrace();
			}
		}
		if (servers.size() == 0) {
			throw new ServiceException("NO IO-Event-Server active");
		}

		// Activate the ticker
		long tickTime = 30000;
		try {
			tickTime = Long.parseLong(p.getParameter(PREFIX + "ticktime", null));
		} catch (Exception ex) {
		}
		myTicker = new Ticker(tickTime);
		myTicker.start();
	}

	/**
    This is called by the JADE Runtime when this service is deinstalled.
    It stops the Ticker thread and all active IO event servers.
	 */
	public void shutdown() {
		// Shutdown the Ticker
		if (myTicker != null) {
			myTicker.shutdown();
		}

		// Shutdown all servers
		Object[] ss = servers.values().toArray();
		for (int i = 0; i < ss.length; ++i) {
			((IOEventServer) ss[i]).shutdown();
		}
	}

	/**
    Retrieve the helper of this Service
	 */
	public ServiceHelper getHelper(Agent a) {
		if (myHelper == null) {
			myHelper = new BEManagementHelperImpl();
		}
		return myHelper;
	}

	/**
    Inner class IOEventServer.
    This class asynchronously manages a server socket and all IO Events
    that happen on it and on all sockets opened through it.
    The BEManagementService is basically a collection of these servers.
	 */
	private class IOEventServer implements PDPContextManager, PDPContextManager.Listener, JICPMediatorManager {

		private String myID;
		private String myLogPrefix;
		private int state = INIT_STATE;
		private ServerSocketChannel mySSChannel;
		private int mediatorCnt = 1;
		private Hashtable mediators = new Hashtable();
		private Vector deregisteredMediators = new Vector();
		private String host;
		private int port;
		private Properties leapProps = new Properties();
		private PDPContextManager myPDPContextManager;
		private TransportProtocol myProtocol;
		private ConnectionFactory myConnectionFactory;
		private LoopManager[] loopers;

		/**
        Initialize this IOEventServer according to the Profile
		 */
		public void init(String id, Profile p) {
			myID = id;
			myLogPrefix = (PREFIX.startsWith(myID) ? "" : "Server " + myID + ": ");

			// Local host
			host = p.getParameter(id + '_' + JICPProtocol.LOCAL_HOST_KEY, null);

			// Local port
			port = DEFAULT_PORT;
			String strPort = p.getParameter(id + '_' + JICPProtocol.LOCAL_PORT_KEY, myID);
			try {
				port = Integer.parseInt(strPort);
			} catch (Exception e) {
				// Keep default
			}

			// Protocol
			/*
			 * Actually we only need a ConnectionFactory at least the way it is build at the moment.
			 *
			 * Perhaps a NIOJICPServer should do the Selector handling, now that is controlled by instances of a inner class
			 * LoopManager.
			 * Than a NIOJICPPeer controls this server and provides the ConnectionFactory
			 *
			 * In that way we stay closer the regular blocking io handling, more work involved though.....
			 *
			 */

			String protoManagerClass = p.getParameter(id + '_' + "protocol", null);
			ProtocolManager pm = null;
			try {
				pm = (ProtocolManager) Class.forName(protoManagerClass).newInstance();
			} catch (Exception e) {
				if (protoManagerClass != null) {
					myLogger.log(Logger.WARNING, myLogPrefix + "Unable to load protocol-manager class " + protoManagerClass + ", fallback to default " + NIOJICPPeer.class.getName()+"!!!!");
				}
				pm = new NIOJICPPeer();
			}
			myLogger.log(Logger.INFO, myLogPrefix + "ProtocolManager class = " + pm.getClass().getName());
			myProtocol = pm.getProtocol();
			myConnectionFactory = pm.getConnectionFactory();

			// Read the LEAP configuration properties
			String fileName = p.getParameter(id + '_' + LEAP_PROPERTY_FILE, LEAP_PROPERTY_FILE_DEFAULT);
			try {
				leapProps.load(fileName);
				myLogger.log(Logger.INFO, myLogPrefix + "Applying properties from file " + fileName + " to all back-ends");
			} catch (Exception e) {
				myLogger.log(Logger.CONFIG, myLogPrefix + "Can't read LEAP property file " + fileName + ". Keep default. [" + e + "]");
				// Ignore: no back end properties specified
			}
			leapProps.setProperty(BackEndContainer.USE_BACKEND_MANAGER, "true");

			// Initialize the PDPContextManager if specified
			String pdpContextManagerClass = leapProps.getProperty(PDP_CONTEXT_MANAGER_CLASS);
			if (pdpContextManagerClass != null) {
				try {
					myLogger.log(Logger.INFO, myLogPrefix + "Loading PDPContextManager of class " + pdpContextManagerClass);
					myPDPContextManager = (PDPContextManager) Class.forName(pdpContextManagerClass).newInstance();
					myPDPContextManager.init(leapProps);
					myPDPContextManager.registerListener(this);
				} catch (Throwable t) {
					myLogger.log(Logger.WARNING, myLogPrefix + "Cannot load PDPContext manager " + pdpContextManagerClass);
					t.printStackTrace();
					myPDPContextManager = null;
				}
			} else {
				// Use itself as default
				myPDPContextManager = this;
			}

			// Looper pool size
			int poolSize = DEFAULT_POOL_SIZE;
			String strPoolSize = p.getParameter(id + '_' + "poolsize", null);
			try {
				poolSize = Integer.parseInt(strPoolSize);
			} catch (Exception e) {
				// Keep default
			}
			loopers = new LoopManager[poolSize];
			for (int i = 0; i < loopers.length; ++i) {
				loopers[i] = new LoopManager(this, i);
			}
		}

		/**
        Start listening for IO events
		 */
		public synchronized void activate() throws Throwable {
			// Create the ServerSocketChannel
			myLogger.log(Logger.CONFIG, myLogPrefix + "Opening server socket channel.");
			mySSChannel = ServerSocketChannel.open();
			mySSChannel.configureBlocking(false);

			// Bind the server socket to the proper host and port
			myLogger.log(Logger.CONFIG, myLogPrefix + "Binding server socket to." + host + ":" + port);
			ServerSocket ss = mySSChannel.socket();
			InetSocketAddress addr = null;
			if (host != null) {
				addr = new InetSocketAddress(host, port);
			} else {
				addr = new InetSocketAddress(port);
				host = Profile.getDefaultNetworkName();
			}
			ss.bind(addr);

			// Register for asynchronous IO events
			myLogger.log(Logger.CONFIG, myLogPrefix + "Registering for asynchronous IO events.");

			// Register with the selector
			mySSChannel.register(getLooper().getSelector(), SelectionKey.OP_ACCEPT);
			myLogger.log(Logger.INFO, myLogPrefix + "Ready to accept I/O events on address " + myProtocol.buildAddress(host, String.valueOf(port), null, null));

			// Start the loop managers
			for (int i = 0; i < loopers.length; ++i) {
				loopers[i].start();
			}
		}

		/**
        @return The port this server is listening for connection on
		 */
		public int getLocalPort() {
			return mySSChannel.socket().getLocalPort();
		}

		/**
        @return The host this server is listening for connection on
		 */
		public String getLocalHost() {
			return host;
		}

		/**
        Make this IOEventServer terminate
		 */
		public synchronized void shutdown() {
			myLogger.log(Logger.CONFIG, myLogPrefix + "Shutting down...");

			try {
				// Close the server socket
				if (mySSChannel != null) {
					mySSChannel.close();
				}
				// Force the looper threads to terminate.
				if (loopers != null) {
					for (int i = 0; i < loopers.length; ++i) {
						loopers[i].stop();
						loopers[i].join();
					}
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}

			// Close all mediators
			synchronized (mediators) {
				Iterator it = mediators.values().iterator();
				while (it.hasNext()) {
					NIOMediator m = (NIOMediator) it.next();
					m.kill();
				}
			}
			mediators.clear();
		}

		final String getID() {
			return myID;
		}

		final String getLogPrefix() {
			return myLogPrefix;
		}

		/**
        Get the LoopManager with the minimum number of registered
        keys.
		 */
		final LoopManager getLooper() {
			int minSize = 999999; // Big value;
			int index = -1;
			for (int i = 0; i < loopers.length; ++i) {
				int size = loopers[i].size();
				if (size < minSize) {
					minSize = size;
					index = i;
				}
			}
			return loopers[index];
		}

		final NIOJICPConnection createConnection(SelectionKey key) throws ICPException {
			Socket s = null;
			NIOJICPConnection conn = (NIOJICPConnection) myConnectionFactory.createConnection(s);
			conn.init((SocketChannel) key.channel());
			if (myLogger.isLoggable(Level.FINE)) {
				myLogger.fine("create connection " + conn.getClass().getName());
			}
			return conn;
		}

		/**
        Serve an IO event conveied by a SelectionKey (a JICPPacket or an IO exception).
        This method is executed by one of the threads in the
        server Thread pool.
		 */
		public void servePacket(KeyManager mgr, JICPPacket pkt) {
			if (pkt == null) {
				return;
			}
			long start = System.currentTimeMillis();

			SelectionKey key = mgr.getKey();
			SocketChannel sc = (SocketChannel) key.channel();
			Socket s = sc.socket();
			InetAddress address = s.getInetAddress();
			int port = s.getPort();

			NIOJICPConnection connection = mgr.getConnection();
			NIOMediator mediator = mgr.getMediator();
			JICPPacket reply = null;
			// If there is no mediator associated to this key prepare to close
			// the connection when the packet will have been processed
			boolean closeConnection = (mediator == null);

			// STEP 1) Serve the received packet
			int type = pkt.getType();
			String recipientID = pkt.getRecipientID();
			try {
				switch (type) {
				case JICPProtocol.GET_ADDRESS_TYPE: {
					// Respond sending back the caller address
					myLogger.log(Logger.INFO, myLogPrefix + "GET_ADDRESS request received from " + address + ":" + port);
					reply = new JICPPacket(JICPProtocol.GET_ADDRESS_TYPE, JICPProtocol.DEFAULT_INFO, address.getHostAddress().getBytes());
					break;
				}
				case JICPProtocol.CREATE_MEDIATOR_TYPE: {
					if (mediator == null) {
						// Create a new Mediator
						myLogger.log(Logger.INFO, myLogPrefix + "CREATE_MEDIATOR request received from " + address + ":" + port);

						Properties p = FrontEndStub.parseCreateMediatorRequest(new String(pkt.getData()));

						// If the platform-name is specified check if it is consistent
						String pn = p.getProperty(Profile.PLATFORM_ID);
						if (pn != null && !pn.equals(platformName)) {
							myLogger.log(Logger.WARNING, myLogPrefix + "Security attack! CREATE_MEDIATOR request with wrong platform name: " + pn);
							reply = new JICPPacket(JICPProtocol.NOT_AUTHORIZED_ERROR, new JADESecurityException("Wrong platform-name"));
							break;
						}

						p.setProperty(BEManagementHelper.FRONT_END_HOST, address.getHostAddress());

						String owner = p.getProperty(JICPProtocol.OWNER_KEY);
						myLogger.log(Logger.CONFIG, myLogPrefix + "Owner = " + owner);
						try {
							Properties pdpContextInfo = myPDPContextManager.getPDPContextInfo(address, owner);
							mergeProperties(p, pdpContextInfo);
						} catch (JADESecurityException jse) {
							myLogger.log(Logger.WARNING, myLogPrefix + "Security attack! CREATE_MEDIATOR request from non authorized address: " + address);
							reply = new JICPPacket(JICPProtocol.NOT_AUTHORIZED_ERROR, jse);
							break;
						}
						// Get mediator ID from the passed properties (if present)
						String id = p.getProperty(JICPProtocol.MEDIATOR_ID_KEY);
						String msisdn = p.getProperty(PDPContextManager.MSISDN);
						if (id != null) {
							if (msisdn != null && !msisdn.equals(id)) {
								// Security attack: Someone is pretending to be someone else
								myLogger.log(Logger.WARNING, myLogPrefix + "Security attack! CREATE_MEDIATOR request with mediator-id != MSISDN. Address is: " + address);
								reply = new JICPPacket(JICPProtocol.NOT_AUTHORIZED_ERROR, new JADESecurityException("Inconsistent mediator-id and msisdn"));
								break;
							}
							// An existing front-end whose back-end was lost. The BackEnd must resynch
							p.setProperty(jade.core.BackEndContainer.RESYNCH, "true");
						} else {
							// Use the MSISDN (if present)
							id = msisdn;
							if (id == null) {
								// Construct a default id using the string representation of the server's TCP endpoint
								id = "BE-" + getLocalHost() + '_' + getLocalPort() + '-' + String.valueOf(mediatorCnt++);
							}
						}

						// If last connection from the same device aborted, the old
						// BackEnd may still exist as a zombie. In case ids are assigned
						// using the MSISDN the new name is equals to the old one.
						if (id.equals(msisdn)) {
							NIOMediator old = (NIOMediator) mediators.get(id);
							if (old != null) {
								// This is a zombie mediator --> kill it
								myLogger.log(Logger.INFO, myLogPrefix + "Replacing old mediator " + id);
								old.kill();
								// Be sure the zombie container has been removed from the Main Container tables
								waitABit(1000);
							}
						}

						// Create and start the new mediator
						mediator = startMediator(id, p);
						configureBlocking(pkt, key);
						closeConnection = !mediator.handleIncomingConnection(connection, pkt, address, port);
						mediators.put(mediator.getID(), mediator);

						if (!closeConnection) {
							// The mediator wants to keep this connection open --> associate
							// it to the current key
							mgr.setMediator(mediator);
						}

						// Create an ad-hoc reply including the assigned mediator-id and the IP address
						p.setProperty(JICPProtocol.MEDIATOR_ID_KEY, mediator.getID());
						p.setProperty(JICPProtocol.LOCAL_HOST_KEY, address.getHostAddress());
						String replyMsg = FrontEndStub.encodeCreateMediatorResponse(p);
						reply = new JICPPacket(JICPProtocol.RESPONSE_TYPE, JICPProtocol.DEFAULT_INFO, replyMsg.getBytes());
						reply.setSessionID((byte) 31); // Dummy session ID != from valid ones
					} else {
						myLogger.log(Logger.WARNING, myLogPrefix + "CREATE_MEDIATOR request received on a connection already linked to an existing mediator");
						reply = new JICPPacket("Unexpected packet type", null);
					}
					break;
				}
				case JICPProtocol.CONNECT_MEDIATOR_TYPE: {
					if (mediator == null) {
						myLogger.log(Logger.INFO, myLogPrefix + "CONNECT_MEDIATOR request received from " + address + ":" + port + ". ID=" + recipientID);

						// FIXME: If there is a PDPContextManager  check that the recipientID is the MSISDN.
						// Where should we get the owner from? It should likely be replicated in each
						// CONNECT_MEDIATOR request.
						/*if (myPDPContextManager != null) {
                            Properties pdpContextInfo = myPDPContextManager.getPDPContextInfo(address, "OWNER???");
                            if (pdpContextInfo != null) {
                            String msisdn = pdpContextInfo.getProperty(PDPContextManager.MSISDN);
                            if (msisdn == null || !msisdn.equals(recipientID)) {
                            myLogger.log(Logger.WARNING, myLogPrefix+"Security attack! CONNECT_MEDIATOR request with mediator-id != MSISDN. Address is: "+address);
                            reply = new JICPPacket(JICPProtocol.NOT_AUTHORIZED_ERROR, null);
                            break;
                            }
                            }
                            else {
                            myLogger.log(Logger.WARNING, myLogPrefix+"Security attack! CONNECT_MEDIATOR request from non authorized address: "+address);
                            reply = new JICPPacket(JICPProtocol.NOT_AUTHORIZED_ERROR, null);
                            break;
                            }
                            }*/

						// Retrieve the mediator to connect to
						mediator = getFromID(recipientID);

						if (mediator != null && mediator.getID() != null) {
							configureBlocking(pkt, key);
							closeConnection = !mediator.handleIncomingConnection(connection, pkt, address, port);
							if (!closeConnection) {
								// The mediator wants to keep this connection open --> associate
								// it to the current key
								mgr.setMediator(mediator);
							}
							reply = new JICPPacket(JICPProtocol.RESPONSE_TYPE, JICPProtocol.DEFAULT_INFO, address.getHostAddress().getBytes());
						} else {
							myLogger.log(Logger.WARNING, myLogPrefix + "Mediator " + recipientID + " not found");
							reply = new JICPPacket(JICPProtocol.NOT_FOUND_ERROR, null);
						}
					} else {
						myLogger.log(Logger.WARNING, myLogPrefix + "CONNECT_MEDIATOR request received on a connection already linked to an existing mediator");
						reply = new JICPPacket("Unexpected packet type", null);
					}
					break;
				}
				default: {
					// Pass all other JICP packets (commands, responses, keep-alives ...)
					// to the proper mediator.
					if (mediator == null) {
						mediator = getFromID(recipientID);
					}

					if (mediator != null) {
						if (myLogger.isLoggable(Logger.FINEST)) {
							myLogger.log(Logger.FINEST, myLogPrefix + "Passing packet of type " + type + " to mediator " + mediator.getID());
						}
						reply = mediator.handleJICPPacket(connection, pkt, address, port);
					} else {
						myLogger.log(Logger.WARNING, myLogPrefix + "No mediator for incoming packet of type " + type);
						if (type == JICPProtocol.COMMAND_TYPE) {
							reply = new JICPPacket(JICPProtocol.NOT_FOUND_ERROR, null);
						}
					}
				}
				} // END of switch
			} catch (Exception e) {
				// Error handling the received packet
				myLogger.log(Logger.WARNING, myLogPrefix + "Error handling incoming packet. ", e);
				// If the incoming packet was a request, send back a generic error response
				if (type == JICPProtocol.COMMAND_TYPE ||
						type == JICPProtocol.CREATE_MEDIATOR_TYPE ||
						type == JICPProtocol.CREATE_MEDIATOR_TYPE ||
						type == JICPProtocol.GET_ADDRESS_TYPE) {
					reply = new JICPPacket("Unexpected error", e);
				}
			}

			// STEP 2) Send back the response if any
			if (reply != null) {
				try {
					connection.writePacket(reply);
				} catch (IOException ioe) {
					myLogger.log(Logger.WARNING, myLogPrefix + "Communication error writing return packet to " + address + ":" + port + " [" + ioe + "]", ioe);
					closeConnection = true;
				}
			} else {
				// The mediator will reply asynchronously --> keep the connection open
				closeConnection = false;
			}

			// STEP 3) Close the connection if necessary
			if (closeConnection) {
				try {
					// Close connection
					if (myLogger.isLoggable(Logger.FINEST)) {
						myLogger.log(Logger.FINEST, myLogPrefix + "Closing connection with " + address + ":" + port);
					}
					connection.close();
				} catch (IOException io) {
					myLogger.log(Logger.WARNING, myLogPrefix + "I/O error while closing connection with " + address + ":" + port);
					io.printStackTrace();
				}
			}

			long end = System.currentTimeMillis();
			if ((end - start) > 100) {
				System.out.println("Serve time = " + (end - start));
			}
		}

		private void configureBlocking(JICPPacket pkt, SelectionKey key) {
			if (pkt.getData().length==1&&pkt.getData()[0]==1) {
				try {
					// TODO: better to use non blocking, but refactoring needed
					key.cancel();
					key.channel().configureBlocking(true);
				} catch (Exception e) {
					myLogger.log(Level.SEVERE, "error configuring blocking", e);
				}
			}
		}

		public void serveException(KeyManager mgr, Exception e) {
			// There was an exception reading the packet. If the current key
			// is associated to a mediator, let it process the exception.
			// Otherwise print a warning.
			NIOJICPConnection connection = mgr.getConnection();
			// If we are closing the connection do not even print the warning
			if (!connection.isClosed()) {
				NIOMediator mediator = mgr.getMediator();
				if (mediator != null) {
					mediator.handleConnectionError(connection, e);
				} else {
					SelectionKey key = mgr.getKey();
					SocketChannel sc = (SocketChannel) key.channel();
					Socket s = sc.socket();
					InetAddress address = s.getInetAddress();
					int port = s.getPort();
					myLogger.log(Logger.WARNING, myLogPrefix + "Exception reading incoming packet from " + address + ":" + port + " [" + e + "]");
				}
			}

			// Always close the connection
			try {
				connection.close();
			} catch (Exception ex) {
			}
		}

		public NIOMediator getFromID(String id) {
			if (id != null) {
				return (NIOMediator) mediators.get(id);
			}
			return null;
		}

		/**
        Called by a Mediator to notify that it is no longer active.
        This is often called within the tick() method. In this case
        directly removing the deregistering mediator from the mediators table
        would cause a ConcurrentModificationException --> We just add
        the deregistering mediator to a queue of mediators to be removed.
        The actual remotion will occur at the next tick in asynchronized
        way.
		 */
		public void deregisterMediator(String id) {
			myLogger.log(Logger.CONFIG, myLogPrefix + "Deregistering mediator " + id);
			deregisteredMediators.add(id);
		}

		public void tick(long currentTime) {
			// Forward the tick to all mediators
			synchronized (mediators) {
				Iterator it = mediators.values().iterator();
				while (it.hasNext()) {
					NIOMediator m = (NIOMediator) it.next();
					m.tick(currentTime);
				}
			}
			// Remove mediators that have deregistered since the last tick
			Object[] dms = null;
			synchronized (deregisteredMediators) {
				dms = deregisteredMediators.toArray();
				deregisteredMediators.clear();
			}
			for (int i = 0; i < dms.length; ++i) {
				synchronized (mediators) {
					NIOMediator m = (NIOMediator) mediators.remove(dms[i]);
					if (m.getID() != null) {
						// A new mediator with the same ID started in the meanwhile.
						// It must not be removed.
						mediators.put(m.getID(), m);
					}
				}
			}
		}

		///////////////////////////////////////
		// PDPContextManager interface
		///////////////////////////////////////
		/**
        If no PDPContextManager is specified in the properties file
        the IOEventServer itself is used and the default behaviour
        is to issue an INCOMING_CONNECTION outgoing command
		 */
		public Properties getPDPContextInfo(InetAddress addr, String owner) throws JADESecurityException {
			GenericCommand cmd = new GenericCommand(INCOMING_CONNECTION, getName(), null);
			cmd.addParam(myID);
			cmd.addParam(addr);
			cmd.addParam(owner);

			try {
				myLogger.log(Logger.CONFIG, myLogPrefix + "Issuing V-Command " + INCOMING_CONNECTION);
				Object ret = submit(cmd);
				if (ret != null) {
					if (ret instanceof Properties) {
						// PDP Context properties detected
						myLogger.log(Logger.FINER, myLogPrefix + "PDPContextProperties for address " + addr + " owner " + owner + " = " + ret);
						return (Properties) ret;
					} else if (ret instanceof JADESecurityException) {
						// Incoming connection from non-authorized device
						myLogger.log(Logger.WARNING, myLogPrefix + "Address " + addr + " owner " + owner + " not authenticated.");
						throw ((JADESecurityException) ret);
					} else if (ret instanceof Throwable) {
						// Unexpected exception
						myLogger.log(Logger.WARNING, myLogPrefix + "Error retrieving PDPContextPropert for address " + addr + " owner " + owner, (Throwable) ret);
						throw new JADESecurityException(((Throwable) ret).getMessage());
					}
				}

				// If we get here, no installed service is able to retrieve
				// the PDPContext properties --> Let all through with
				// empty properties
				return new Properties();
			} catch (ServiceException se) {
				// Should never happen
				se.printStackTrace();
				return null;
			}
		}

		public void init(Properties pp) {
			// Just do nothing
		}

		public void registerListener(PDPContextManager.Listener l) {
			// Just do nothing
		}

		///////////////////////////////////////
		// PDPContextManager.Listener interface
		///////////////////////////////////////
		/**
        Called by the PDPContextManager (if any)
		 */
		public void handlePDPContextClosed(String id) {
			// FIXME: to be implemented
		}

		protected NIOMediator startMediator(String id, Properties p) throws Exception {
			String className = p.getProperty(JICPProtocol.MEDIATOR_CLASS_KEY);
			if (className == null) {
				// Default NIOMediator class
				className = NIOBEDispatcher.class.getName();
			}
			NIOMediator m = (NIOMediator) Class.forName(className).newInstance();
			mergeProperties(p, leapProps);
			myLogger.log(Logger.CONFIG, myLogPrefix + "Initializing mediator " + id + " with properties " + p);
			m.init(this, id, p);
			return m;
		}
	} // END of inner class IOEventServer


	/**
    Inner class KeyManager
    Keep a SelectionKey together with the information associated to it
    such as the connection wrapping the key channel and the mediator
    using that connection (if any).
	 */
	private class KeyManager {

		private SelectionKey key;
		private NIOJICPConnection connection;
		private NIOMediator mediator;
		private IOEventServer server;

		public KeyManager(SelectionKey k, NIOJICPConnection c, IOEventServer s) {
			key = k;
			connection = c;
			server = s;
		}

		public final NIOMediator getMediator() {
			return mediator;
		}

		public final void setMediator(NIOMediator m) {
			mediator = m;
		}

		public final NIOJICPConnection getConnection() {
			return connection;
		}

		public final SelectionKey getKey() {
			return key;
		}

		/**
        Read some data from the connection associated to the managed key
        and let the IOEventServer serve it
		 */
		 public final void read() {
			 try {
				 JICPPacket pkt = connection.readPacket();
				 server.servePacket(this, pkt);
			 } catch (PacketIncompleteException pie) {
				 // The data ready to be read is not enough to complete
				 // a packet. Just do nothing and wait until more data is ready
				 if (myLogger.isLoggable(Level.FINE)) {
					 myLogger.fine("waiting for more data..");
				 }
			 } catch (Exception e) {
				 server.serveException(this, e);
			 }
		 }

		 // TO BE REMOVED
		 public void handleExtraData() {
			 System.out.println("####### handleExtraData()");
			 //final Exception e = new Exception("DUMMY!!!!!!!!!!!");
			 Thread t = new Thread(new Runnable() {

				 public void run() {
					 read();
					 //e.printStackTrace();
				 }
			 });
			 t.setName(Thread.currentThread().getName()+"HED");
			 t.start();
		 }
	} // END of inner class KeyManager
	
	
	/**
	 * Inner class LoopManager
	 */
	private class LoopManager implements Runnable {

		private int myIndex;
		private String displayId;
		private int state = INIT_STATE;
		private Selector mySelector;
		private Thread myThread;
		private IOEventServer myServer;
		private boolean pendingChannelPresent = false;
		private List pendingChannels = new ArrayList();

		public LoopManager(IOEventServer server, int index) {
			myServer = server;
			myIndex = index;
			String id = myServer.getID();
			displayId = "BEManagementService" + (PREFIX.startsWith(id) ? "" : "-" + id);

			try {
				mySelector = Selector.open();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		public void start() {
			state = ACTIVE_STATE;
			String id = myServer.getID();
			String serverId = (PREFIX.startsWith(id) ? "" : "-" + id);
			myThread = new Thread(this);
			myThread.setName(displayId + "-T" + myIndex);
			myThread.start();
		}

		public void stop() {
			state = TERMINATING_STATE;
			mySelector.wakeup();
		}

		public void join() throws InterruptedException {
			myThread.join();
		}

		public void run() {
			while (state == ACTIVE_STATE) {
				int n = 0;
				try {
					// Wait for the next IO events
					n = mySelector.select();
				} catch (NullPointerException npe) {
					// There seems to be a bug in java.nio (http://www.limewire.org/techdocs/nio2.html).
					// Just retry.
					myLogger.log(Logger.WARNING, myServer.getLogPrefix() + "NullPointerException in select. Ignore and retry.");
					continue;
				} catch (Exception e) {
					if (state == ACTIVE_STATE) {
						myLogger.log(Logger.WARNING, myServer.getLogPrefix() + "Error selecting next IO event. ", e);
						// Abort
						state = ERROR_STATE;
					}
				}
				if (state == ACTIVE_STATE) {
					if (n > 0) {
						Set keys = mySelector.selectedKeys();
						Iterator it = keys.iterator();
						while (it.hasNext()) {
							SelectionKey key = (SelectionKey) it.next();
							if (key.isValid()) {
								if ((key.readyOps() & SelectionKey.OP_ACCEPT) != 0) {
									// This is an incoming connection. The channel must be the SerevrSocketChannel server
									if (myLogger.isLoggable(Logger.FINER)) {
										myLogger.log(Logger.FINER, myServer.getLogPrefix() + "------------------ ACCEPT_OP on key "+key);
									}
									handleAcceptOp(key);
								} else if ((key.readyOps() & SelectionKey.OP_READ) != 0) {
									try {
										// This is some incoming data for one of the BE
										if (myLogger.isLoggable(Logger.FINER)) {
											myLogger.log(Logger.FINER, myServer.getLogPrefix() + "READ_OP on key "+key);
										}
										handleReadOp(key);
									} catch (ICPException ex) {
										myLogger.log(Level.SEVERE, "failed to read from socket", ex);
									}
								}
							}
							it.remove();
						}
					}
					handlePendingChannels();
				}
			} // END of while

			state = TERMINATED_STATE;
		}

		private final void handleAcceptOp(SelectionKey key) {
			try {
				SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();

				checkAddress(sc);

				sc.configureBlocking(false);
				LoopManager lm = myServer.getLooper();
				lm.register(sc);
			} catch (JADESecurityException jse) {
				myLogger.log(Logger.WARNING, myServer.getLogPrefix() + "Connection attempt from malicious address " + jse.getMessage());
			} catch (Exception e) {
				myLogger.log(Logger.WARNING, myServer.getLogPrefix() + "Error accepting incoming connection. " + e);
				e.printStackTrace();
			}
		}

		private final void handleReadOp(SelectionKey key) throws ICPException {
			KeyManager mgr = (KeyManager) key.attachment();
			if (mgr == null) {
				mgr = new KeyManager(key, myServer.createConnection(key), myServer);
				key.attach(mgr);
			}
			mgr.read();
		}

		private synchronized final void register(SocketChannel sc) {
			pendingChannels.add(sc);
			pendingChannelPresent = true;
			mySelector.wakeup();
		}

		private synchronized final void handlePendingChannels() {
			if (pendingChannelPresent) {
				for (int i = 0; i < pendingChannels.size(); ++i) {
					SocketChannel sc = (SocketChannel) pendingChannels.get(i);
					//System.out.println(Thread.currentThread().getName()+": Registering channel on Selector "+mySelector);
					try {
						sc.register(mySelector, SelectionKey.OP_READ);
						//System.out.println(Thread.currentThread().getName()+": Done");
					} catch (Exception e) {
						myLogger.log(Logger.WARNING, myServer.getLogPrefix() + "Error registering socket channel for asynchronous IO. " + e);
						e.printStackTrace();
					}
				}
				pendingChannels.clear();
				pendingChannelPresent = false;
			}
		}

		public final Selector getSelector() {
			return mySelector;
		}

		public final int size() {
			return mySelector.keys().size();
		}
	} // END of inner class LoopManager

	/**
	 * Inner class Ticker
	 */
	private class Ticker extends Thread {

		private long period;
		private boolean active = false;

		private Ticker(long period) {
			super();
			this.period = period;
		}

		public void start() {
			active = true;
			setName("BEManagementService-ticker");
			super.start();
		}

		public void run() {
			while (active) {
				try {
					Thread.sleep(period);
					long currentTime = System.currentTimeMillis();
					Object[] ss = servers.values().toArray();
					for (int i = 0; i < ss.length; ++i) {
						((IOEventServer) ss[i]).tick(currentTime);
					}
				} catch (Throwable t) {
					if (active) {
						myLogger.log(Logger.WARNING, "BEManagementService-Ticker: Unexpected exception " + t);
					}
				}
			}
		}

		public void shutdown() {
			active = false;
			interrupt();
		}
	} // END of inner class Ticker

	/**
	 * Inner class BEManagementHelperImpl.
	 * Implementation class for the BEManagementHelper.
	 */
	private class BEManagementHelperImpl implements BEManagementHelper {

		public void init(Agent a) {
			// Just do nothing;
		}

		public String getProperty(String containerName, String key) {
			String value = null;
			NIOMediator mediator = findMediatorGlobally(containerName);
			if (mediator != null) {
				Properties pp = mediator.getProperties();
				if (pp != null) {
					value = pp.getProperty(key);
				}
			}
			return value;
		}

		private NIOMediator findMediatorGlobally(String id) {
			Object[] ss = servers.values().toArray();
			for (int i = 0; i < ss.length; ++i) {
				NIOMediator m = ((IOEventServer) ss[i]).getFromID(id);
				if (m != null) {
					return m;
				}
			}
			return null;
		}
	} // END of inner class BEManagementHelperImpl

	///////////////////////////////////////
	// Utility methods
	///////////////////////////////////////
	private void mergeProperties(Properties p1, Properties p2) {
		Enumeration e = p2.propertyNames();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			p1.setProperty(key, p2.getProperty(key));
		}
	}

	private void waitABit(long t) {
		try {
			Thread.sleep(t);
		} catch (InterruptedException ie) {
		}
	}

	/**
    Check that the address of the initiator of a new connection
    is not in the list of addresses considered as malicious.
	 */
	private final void checkAddress(SocketChannel sc) throws JADESecurityException {
		Socket s = sc.socket();
		InetAddress address = s.getInetAddress();
		if (maliciousAddresses.contains(address)) {
			try {
				sc.close();
			} catch (Exception e) {
			}
			throw new JADESecurityException(address.toString());
		}
	}
}

