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

package jade.imtp.leap;

import jade.core.IMTPException;
import java.util.Vector;
import java.util.Enumeration;
import jade.util.Logger;

/**
 * Class declaration
 * @author Giovanni Caire - TILAB
 */
public class MicroStub {
	protected Dispatcher myDispatcher;
	protected Vector pendingCommands = new Vector();
	private int activeCnt = 0;
	private boolean flushing = false;
	private Thread flusher;
	protected Logger logger;
	
	public MicroStub(Dispatcher d) {
		myDispatcher = d;
		logger = Logger.getMyLogger(getClass().getName());
	}
	
	protected Command executeRemotely(Command c, long timeout) throws IMTPException {
		try {
			disableFlush();
			byte[] cmd = SerializationEngine.serialize(c);
			byte[] rsp = myDispatcher.dispatch(cmd, flushing);
			if (pendingCommands.size() > 0) {
				System.out.println("############# Dispatch succeeded with "+pendingCommands.size()+" pending commands.");
			}
			Command r = SerializationEngine.deserialize(rsp);
			if (r.getCode() == Command.ERROR) {
				if (!((Boolean) r.getParamAt(0)).booleanValue()) {
					// Unexpected exception thrown in the remote site
					String msg = new String("Exception "+(String) r.getParamAt(1)+" occurred in remote site processing command "+c.getCode()+". "+(String) r.getParamAt(2));
					logger.log(Logger.SEVERE,msg);
					throw new IMTPException(msg);
				}
				else if (((String) r.getParamAt(1)).equals("jade.core.IMTPException")) {
					throw new IMTPException((String) r.getParamAt(2));
				}
			}
			return r;
		}
		catch (ICPException icpe) {
			// The destination is unreachable. 
			if (timeout == 0) {
				// The command can't be postponed
				throw new IMTPException("Destination unreachable", icpe);
			}
			else {
				logger.log(Logger.WARNING, "Dispatch failed. Command postponed. "+icpe.getMessage());
				// FIXME: if timeout > 0 we should add a timer
				postpone(c);
				return null;
			}
		}
		catch (LEAPSerializationException lse) {
			throw new IMTPException("Serialization error", lse);
		}
		finally {
			enableFlush();
		}
	}
	
	private void postpone(Command c) {
		if (logger.isLoggable(Logger.FINE)) {
			logger.log(Logger.FINE,Thread.currentThread().toString()+": Command "+c.getCode()+" postponed");
		}
		pendingCommands.addElement(c);
		int size = pendingCommands.size();
		if (size > 100 && size < 110) {
			logger.log(Logger.WARNING,size+" postponed commands");
		}
	}
	
	
	public boolean flush() {
		if (pendingCommands.size() > 0) {
			// This is called by the main thread of the underlying EndPoint
			// --> The actual flushing must be done asynchronously to avoid
			// deadlock
			flusher = new Thread() {
				public void run() {
					// 1) Lock the buffer of pending commands to avoid calling 
					// remote methods while flushing
					synchronized (pendingCommands) {
						while (activeCnt > 0) {
							try {
								pendingCommands.wait();
							}
							catch (InterruptedException ie) {
							}
						}
						flushing = true;
					}
					
					// 2) Flush the buffer of pending commands
					logger.log(Logger.INFO,"Start flushing");					
					int flushedCnt = 0;
					Command c = null;
					while ((c = removeFirst()) != null) {
						// Exceptions and return values of commands whose delivery
						// was delayed for disconnection problems can and must not
						// be handled!!!
						try {
							if (logger.isLoggable(Logger.FINE)) {
								logger.log(Logger.FINE,"Flushing command: code = "+c.getCode());
							}
							Command r = executeRemotely(c, 0);
							flushedCnt++;
							if (r.getCode() == Command.ERROR) {
								logger.log(Logger.SEVERE,"Remote exception in command asynchronous delivery. "+r.getParamAt(2));
							}
						}
						catch (Exception ex) {
							logger.log(Logger.WARNING,"Exception in command asynchronous delivery. "+ex);
							// We are disconnected again --> put the command back in the queue of postponed commands
							// and stop flushing
							pendingCommands.insertElementAt(c, 0);
							break;
						}
					}
					
					// 3) Unlock the buffer of pending commands
					System.out.println("########## "+pendingCommands.size()+" pending commands after flush");
					synchronized (pendingCommands) {
						flushing = false;
						pendingCommands.notifyAll();
					}
					logger.log(Logger.INFO,"Flushing thread terminated ("+flushedCnt+")");
				}
			};
			flusher.start();
			return true;
		}
		return false;
	}
	
	public boolean isEmpty() {
		return ((pendingCommands.size() == 0) && (!flushing));
	}
	
	/**
	 Note that normal command-dispatching and postponed command
	 flushing can't occur at the same time, but different commands
	 can be dispatched in parallel. This is the reason for this
	 lock/unlock mechanism instead of a simple synchronization.
	 */
	private void disableFlush() {
		if (Thread.currentThread() != flusher) {
			synchronized (pendingCommands) {
				while (flushing) {
					try {
						pendingCommands.wait();
					}
					catch (InterruptedException ie) {
					}
				}
				activeCnt++;
			}
		}
	}
	
	private void enableFlush() {
		if (Thread.currentThread() != flusher) {
			synchronized (pendingCommands) {
				activeCnt--;
				if (activeCnt == 0) {
					pendingCommands.notifyAll();
				}
			}
		}
	}		
	
	private Command removeFirst() {
		synchronized (pendingCommands) {
			Command c = null;
			if (pendingCommands.size() > 0) {
				c = (Command) pendingCommands.elementAt(0);
				pendingCommands.removeElementAt(0);
			}
			return c;
		}
	}
}

