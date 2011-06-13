package jade.core;

//#J2ME_EXCLUDE_FILE
//#APIDOC_EXCLUDE_FILE

import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.leap.Properties;
import jade.imtp.leap.JICP.PDPContextManager;
import jade.imtp.leap.JICP.JICPProtocol;

import java.io.*;

public class ScalabilityTest {
	private static final String CONTENT_SIZE = "s";
	private static final int DEFAULT_CONTENT_SIZE = 1000;
	
	private static final String TIME_INTERVAL = "t";
	private static final long DEFAULT_TIME_INTERVAL = 1000;
	private static final long STEPBYSTEP_TIME_INTERVAL = -1;
	
	private static final String N_ITERATIONS = "i";
	private static final int DEFAULT_N_ITERATIONS = -1;
	
	private static final String N_COUPLES = "n";
	private static final int DEFAULT_N_COUPLES = 10;
	
	private static final String BASE = "base";
	private static final int DEFAULT_BASE = 0;
		
	private static final String MODE = "mode";
	private static final String FAST_MODE_S = "fast";
	private static final String SLOW_MODE_S = "slow";
	private static final String STEP_BY_STEP_MODE_S = "stepbystep";
	private static final String READY_GO_MODE_S = "readygo";
	private static final int FAST_MODE = 0;
	private static final int SLOW_MODE = 1;
	private static final int STEP_BY_STEP_MODE = 2;
	private static final int READY_GO_MODE = 3;
	
	private static final String MEASURE = "measure";
	private static final String BITRATE_MEASURE_S = "bitrate";
	private static final String RTT_MEASURE_S = "rtt";
	private static final int BITRATE_MEASURE = 0;
	private static final int RTT_MEASURE = 1;
	
	private static Object terminatedLock = new Object();
	private static Object readyLock = new Object();	
	private static Object semaphore = new Object();
	
	private static byte[] content;
	private static long timeInterval;
	private static int nIterations;
	private static int nCouples;
	private static int base;
	private static int mode; 
	private static int measure;
	private static int readyCnt = 0;
	private static int terminatedCnt = 0;
	
	private static long totalTime = 0;
	private static long totalTime2 = 0;

	private static Object lock = new Object();
	private static BufferedReader inputReader;
	
	public static void main(String[] args) {
		Properties pp = parseArguments(args);
		
		int contentSize = DEFAULT_CONTENT_SIZE;
		try {
			contentSize = Integer.parseInt(pp.getProperty(CONTENT_SIZE));
		}
		catch (Exception e) {
			// Keep default
		}
		content = new byte[contentSize];
		
		timeInterval = DEFAULT_TIME_INTERVAL;
		try {
			timeInterval = Long.parseLong(pp.getProperty(TIME_INTERVAL));
		}
		catch (Exception e) {
			// Keep default
		}
		
		nIterations = DEFAULT_N_ITERATIONS;
		try {
			nIterations = Integer.parseInt(pp.getProperty(N_ITERATIONS));
		}
		catch (Exception e) {
			// Keep default
		}
		
		base = DEFAULT_BASE;
		try {
			base = Integer.parseInt(pp.getProperty(BASE));
		}
		catch (Exception e) {
			// Keep default
		}
		
		mode = READY_GO_MODE;
		try {
			String modeStr = pp.getProperty(MODE);
			if (SLOW_MODE_S.equals(modeStr)) {
				mode = SLOW_MODE;
			}
			else if (FAST_MODE_S.equals(modeStr)) {
				mode = FAST_MODE;
			}
			else if (STEP_BY_STEP_MODE_S.equals(modeStr)) {
				mode = STEP_BY_STEP_MODE;
			}
		}
		catch (Exception e) {
			// Keep default
		}
		// Prepare the inputReader to get user inputs if necessary
		if (mode == READY_GO_MODE || mode == STEP_BY_STEP_MODE) {
			inputReader = new BufferedReader(new InputStreamReader(System.in));
		}
		
		nCouples = DEFAULT_N_COUPLES;
		try {
			nCouples = Integer.parseInt(pp.getProperty(N_COUPLES));
		}
		catch (Exception e) {
			// Keep default
		}
		
		measure = BITRATE_MEASURE;
		try {
			String measureStr = pp.getProperty(MEASURE);
			if (RTT_MEASURE_S.equals(measureStr)) {
				measure = RTT_MEASURE;
			}
		}
		catch (Exception e) {
			// Keep default
		}
		
		String prefix = Profile.getDefaultNetworkName();
		for (int i = base; i < base+nCouples; i++) {
			initCouple(pp.getProperty(MicroRuntime.HOST_KEY), pp.getProperty(MicroRuntime.PORT_KEY), pp.getProperty(MicroRuntime.CONN_MGR_CLASS_KEY), pp.getProperty(JICPProtocol.MEDIATOR_CLASS_KEY), pp.getProperty(JICPProtocol.MAX_DISCONNECTION_TIME_KEY), prefix, i);
			switch (mode) {
			case SLOW_MODE:
				waitABit();
				break;
			case STEP_BY_STEP_MODE:
				prompt("Couple #"+i+" started. Press enter to continue");
				break;
			default:
				Thread.currentThread().yield();
			}
		}
		
		waitUntilReady();
		if (mode == READY_GO_MODE) {
			prompt("All "+nCouples+" couples ready. Press enter to go");
		}	
		start();
		if (nIterations > 0) {
			System.out.println("Measurement started....");
		}
		
		if (timeInterval == STEPBYSTEP_TIME_INTERVAL) {
			int i = 0;
			while (true) {
				waitUntilReady();
				prompt("Iteration # "+i+" terminated by all couples. Press enter to go");
				++i;
				start();
			}
		}
	}
	
	private static void notifyReady() {
		synchronized (semaphore) {
			synchronized (readyLock) {
				readyCnt++;
				readyLock.notifyAll();
			}
			try {
				semaphore.wait();
			}
			catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}
	
	private static void waitUntilReady() {
		synchronized (readyLock) {
			while (readyCnt < nCouples) {
				try {
					readyLock.wait();
				}
				catch (InterruptedException ie) {
					ie.printStackTrace();
				}
			}
		}
	}
	
	private static void start() {
		synchronized (semaphore) {
			semaphore.notifyAll();			
			readyCnt = 0;
		}
	}
				
		
	private static void waitABit() {
		synchronized (lock) {
			try {
				lock.wait(1000);
			}
			catch (Exception e) {}
		}
	}

	private static void prompt(String msg) {
		System.out.println(msg);
		try {
			inputReader.readLine();
		}
		catch (IOException ioe) {
		}
	}
	
	private static Properties parseArguments(String[] args) {
  	Properties props = new Properties();
  	int i = 0;
  	while (i < args.length) {
  		if (args[i].startsWith("-")) {
  			// Parse next option
				String name = args[i].substring(1);
  			if (++i < args.length) {
  				props.setProperty(name, args[i]);
  			}
  			else {
  				throw new IllegalArgumentException("No value specified for property \""+name+"\"");
  			}
  			++i;
  		}
  		else {
				throw new IllegalArgumentException("Invalid property \""+args[i]+"\". It does not start with '-'");
  		}
  	}
	
  	return props;
	}

	private static void initCouple(String host, String port, String connectionManager, String mediatorClass, String maxDiscTime, String prefix, int index) {
		String senderClass = "jade.core.ScalabilityTest$BitrateSenderAgent";
		String receiverClass = "jade.core.ScalabilityTest$BitrateReceiverAgent";
		if (measure == RTT_MEASURE) {
			senderClass = "jade.core.ScalabilityTest$RTTSenderAgent";
			receiverClass = "jade.core.ScalabilityTest$RTTReceiverAgent";
		}
		
		Properties pp = new Properties();
		if (host != null) {
			pp.setProperty(MicroRuntime.HOST_KEY, host);
		}
		if (port != null) {
			pp.setProperty(MicroRuntime.PORT_KEY, port);
		}
		if (connectionManager != null) {
			pp.setProperty(MicroRuntime.CONN_MGR_CLASS_KEY, connectionManager);
		}
		if (mediatorClass != null) {
			pp.setProperty(JICPProtocol.MEDIATOR_CLASS_KEY, mediatorClass);
		}
		if (maxDiscTime != null) {
			pp.setProperty(JICPProtocol.MAX_DISCONNECTION_TIME_KEY, maxDiscTime);
		}
		String sName = "S-"+prefix+"-"+index;
		pp.setProperty(PDPContextManager.MSISDN, sName);
		String rName = "R-"+prefix+"-"+index;
		String prop = sName+":"+senderClass+"("+rName+")";
		pp.setProperty(MicroRuntime.AGENTS_KEY, prop);
		pp.setProperty(JICPProtocol.KEEP_ALIVE_TIME_KEY, "-1");
		FrontEndContainer fes = new FrontEndContainer(pp);
		
		pp = new Properties();
		if (host != null) {
			pp.setProperty(MicroRuntime.HOST_KEY, host);
		}
		if (port != null) {
			pp.setProperty(MicroRuntime.PORT_KEY, port);
		}
		if (connectionManager != null) {
			pp.setProperty(MicroRuntime.CONN_MGR_CLASS_KEY, connectionManager);
		}
		if (mediatorClass != null) {
			pp.setProperty(JICPProtocol.MEDIATOR_CLASS_KEY, mediatorClass);
		}
		if (maxDiscTime != null) {
			pp.setProperty(JICPProtocol.MAX_DISCONNECTION_TIME_KEY, maxDiscTime);
		}
		pp.setProperty(PDPContextManager.MSISDN, rName);
		prop = rName+":"+receiverClass;
		pp.setProperty(MicroRuntime.AGENTS_KEY, prop);
		pp.setProperty(JICPProtocol.KEEP_ALIVE_TIME_KEY, "-1");
		FrontEndContainer fer = new FrontEndContainer(pp);
	}
	
	private static void notifyTerminated(long time, long time2) {
		synchronized (terminatedLock) {
			totalTime += time;
			totalTime2 += time2;
			terminatedCnt++;
			if (terminatedCnt == nCouples) {
				// All couples have terminated. Compute the average round-trip time
				long n = nCouples * ((long) nIterations);
				if (measure == BITRATE_MEASURE) {
					double totBytes = n * ((double) content.length);
					double averageBitrate = totBytes / totalTime;
					System.out.println("----------------------------------\nTest completed successufully.\nAverage bitrate (Kbyte/s) = "+averageBitrate+"\n----------------------------------");
				}
				else if (measure == RTT_MEASURE) {
					long averageRoundTripTime = totalTime / n;
					double avg = (double) averageRoundTripTime;
					double x = totalTime2 + n*avg*avg - 2*avg*totalTime;
					double standardDeviation = Math.sqrt(x / n);
					System.out.println("----------------------------------\nTest completed successufully.\nAverage round trip time = "+averageRoundTripTime+" ms\nStandard deviation = "+standardDeviation+"\n----------------------------------");
				}
				System.exit(0);
			}
		}
	}

	
	/**
	   Inner class BitrateSenderAgent
	 */
	public static class BitrateSenderAgent extends Agent {
		private ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		private AID myReceiver;
		
		protected void setup() {
			Object[] args = getArguments();
			if (args != null && args.length == 1) {
				myReceiver = new AID((String) args[0], AID.ISLOCALNAME);
			}
			else {
				System.out.println("Missing receiver name !!!!!");
				doDelete();
				return;
			}
			msg.addReceiver(myReceiver);
			msg.setByteSequenceContent(content);
			
			System.out.println("Sender "+getName()+" ready: my receiver is "+myReceiver.getName());
			notifyReady();
			
			if (timeInterval > 0) {
				addBehaviour(new TickerBehaviour(this, timeInterval) {
					public void onTick() {
						job();
					}
				} );
			}
			else {
				addBehaviour(new CyclicBehaviour(this) {
					public void action() {
						job();
					}
				} );
			}
		}
		
		private void job() {
			send(msg);
			if (timeInterval == STEPBYSTEP_TIME_INTERVAL) {
				notifyReady();
			}
		}
	} // END of inner class BitrateSenderAgent
	
	
	/**
	   Inner class BitrateReceiverAgent
	 */
	public static class BitrateReceiverAgent extends Agent {
		private boolean firstReceived = false;
		private boolean terminated = false;
		private long startTime;
		private int cnt = 0;
		
		protected void setup() {
			addBehaviour(new CyclicBehaviour(this) {
				public void action() {
					ACLMessage msg = myAgent.receive();
					if (msg != null) {
						if (!firstReceived) {
							firstReceived = true;
							startTime = System.currentTimeMillis();
						}
						if (!terminated) {
							if (nIterations > 0 && cnt >= nIterations) {
								long endTime = System.currentTimeMillis();
								long totalCoupleTime = endTime - startTime;
								long totalCoupleTime2 = totalCoupleTime*totalCoupleTime;
								notifyTerminated(totalCoupleTime, totalCoupleTime2);
								terminated = true;
							}
							cnt++;
						}
					}
					else {
						block();
					}
				}
			} );
		}
	} // END of inner class BitrateReceiverAgent
	
	
	/**
	   Inner class RTTSenderAgent
	 */
	public static class RTTSenderAgent extends Agent {
		private ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		private AID myReceiver;
		private MessageTemplate myTemplate;
		
		private long totalCoupleTime = 0;
		private long totalCoupleTime2 = 0;
		private boolean terminated = false;
		
		private int cnt = 0;
		
		protected void setup() {
			Object[] args = getArguments();
			if (args != null && args.length == 1) {
				myReceiver = new AID((String) args[0], AID.ISLOCALNAME);
			}
			else {
				System.out.println("Missing receiver name !!!!!");
				doDelete();
				return;
			}
			msg.addReceiver(myReceiver);
			msg.setByteSequenceContent(content);
			myTemplate = MessageTemplate.MatchSender(myReceiver);
			
			notifyReady();
			
			if (timeInterval > 0) {
				addBehaviour(new TickerBehaviour(this, timeInterval) {
					public void onTick() {
						job();
					}
				} );
			}
			else {
				addBehaviour(new CyclicBehaviour(this) {
					public void action() {
						job();
					}
				} );
			}				
		}

		private void job() {
			long start = System.currentTimeMillis();
			send(msg);
			blockingReceive(myTemplate);
			long time = System.currentTimeMillis() - start;
			
			if (!terminated) {
				totalCoupleTime += time;
				totalCoupleTime2 += (time*time);
				if (nIterations > 0 && (++cnt) >= nIterations) {
					notifyTerminated(totalCoupleTime, totalCoupleTime2);
					terminated = true;
				}
			}
			if (timeInterval == STEPBYSTEP_TIME_INTERVAL) {
				notifyReady();
			}
		}
	} // END of inner class RTTSenderAgent
	
	
	/**
	   Inner class RTTReceiverAgent
	 */
	public static class RTTReceiverAgent extends Agent {
		protected void setup() {
			addBehaviour(new CyclicBehaviour(this) {
				public void action() {
					ACLMessage msg = myAgent.receive();
					if (msg != null) {
						ACLMessage reply = msg.createReply();
						reply.setPerformative(ACLMessage.INFORM);
						reply.setByteSequenceContent(msg.getByteSequenceContent());
						myAgent.send(reply);
					}
					else {
						block();
					}
				}
			} );
		}
	} // END of inner class RTTReceiverAgent
	
}