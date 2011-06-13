package jade.core.behaviours;

//#J2ME_EXCLUDE_FILE

import jade.core.behaviours.CyclicBehaviour;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jade.content.AgentAction;
import jade.content.ContentElement;
import jade.content.ContentException;
import jade.content.ContentManager;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Done;
import jade.content.onto.basic.Result;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.ExceptionVocabulary;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.ConversationList;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;
import jade.util.Logger;

/**
 * Ready made behaviour that for each incoming message automatically invoke a corresponding method of the form<br>
 * <code>
 * public void serveCcccPppp(Cccc c, ACLMessage msg) throws Exception
 * </code>
 * <br>
 * where c represents the key content-element referenced by the incoming message msg.<br>
 * ContentElement-s representing SL0 operators action, done and result are automatically managed
 * so that for instance if an incoming REQUEST message is received carrying a content of type<br>
 * ((action (actor ...) (Sell ...)))<br>
 * a serving method with signature<br>
 * <code>
 * public void serveSellRequest(Sell s, ACLMessage msg) throws Exception
 * </code>
 * <br>
 * will be searched.<br>
 * Serving methods are responsible for sending back responses if any.  
 */
public class OntologyServer extends CyclicBehaviour {
	private static final long serialVersionUID = -2997404961058073783L;

	private static final String[] performativeNames = new String[22];
	static { 
		performativeNames[ACLMessage.ACCEPT_PROPOSAL]="AcceptProposal";
		performativeNames[ACLMessage.AGREE]="Agree";
		performativeNames[ACLMessage.CANCEL]="Cancel";
		performativeNames[ACLMessage.CFP]="Cfp";
		performativeNames[ACLMessage.CONFIRM]="Confirm";
		performativeNames[ACLMessage.DISCONFIRM]="Disconfirm";
		performativeNames[ACLMessage.FAILURE]="Failure";
		performativeNames[ACLMessage.INFORM]="Inform";
		performativeNames[ACLMessage.INFORM_IF]="InformIf";
		performativeNames[ACLMessage.INFORM_REF]="InformRef";
		performativeNames[ACLMessage.NOT_UNDERSTOOD]="NotUNderstood";
		performativeNames[ACLMessage.PROPOSE]="Propose";
		performativeNames[ACLMessage.QUERY_IF]="QueryIf";
		performativeNames[ACLMessage.QUERY_REF]="QueryRef";
		performativeNames[ACLMessage.REFUSE]="Refuse";
		performativeNames[ACLMessage.REJECT_PROPOSAL]="RejectProposal";
		performativeNames[ACLMessage.REQUEST]="Request";
		performativeNames[ACLMessage.REQUEST_WHEN]="RequestWhen";
		performativeNames[ACLMessage.REQUEST_WHENEVER]="RequestWhenever";
		performativeNames[ACLMessage.SUBSCRIBE]="Subscribe";
		performativeNames[ACLMessage.PROXY]="Proxy";
		performativeNames[ACLMessage.PROPAGATE]="Propagate";
	}
	
	private Object serverDelegate;
	private Ontology onto;
	private Codec codec;
	private int[] servedPerformatives;
	
	private ConversationList ignoredConversations;
	private MessageTemplate template;
	
	private Map<String, Method> cachedMethods = new HashMap<String, Method>();
	private ContentElement receivedContentElement;
	
	protected Logger myLogger = Logger.getMyLogger(getClass().getName());
	
	public OntologyServer(Agent a, Ontology onto, int performative) {
		this(a, onto, new int[]{performative}, null);
	}
	
	public OntologyServer(Agent a, Ontology onto, int[] performatives) {
		this(a, onto, performatives, null);
	}
	
	public OntologyServer(Agent a, Ontology onto, int performative, Object serverDelegate) {
		this(a, onto, new int[]{performative}, serverDelegate);
	}
	
	public OntologyServer(Agent a, Ontology onto, int[] performatives, Object serverDelegate) {
		super(a);
		
		this.onto = onto;
		servedPerformatives = performatives;
		this.serverDelegate = (serverDelegate != null ? serverDelegate : this);
		if (servedPerformatives != null) {
			if (servedPerformatives.length == 1) {
				// E.g. XXX-Ontology-Request-Server
				setBehaviourName(onto.getName()+"-"+performativeNames[servedPerformatives[0]]+"-Serever");
			}
			else {
				// E.g. XXX-Ontology-Request...-Server
				setBehaviourName(onto.getName()+"-"+performativeNames[servedPerformatives[0]]+"...-Serever");
			}
		}
		else {
			// E.g. XXX-Ontology-Server
			setBehaviourName(onto.getName()+"-Serever");
		}
	}
	
	public void setLanguage(Codec codec) {
		this.codec = codec;
	}
	
	public void setMessageTemplate(MessageTemplate template) {
		this.template = template;
	}
	
	public void onStart() {
		ignoredConversations = new ConversationList(myAgent);
		
		// Unless a template is explicitly set, we get messages matching the ontology, the served performatives.
		if (template == null) {
			if (servedPerformatives != null) {
				template = MessageTemplate.and(
						MessageTemplate.MatchOntology(onto.getName()),
							new MessageTemplate(new MatchExpression() {
								public boolean match(ACLMessage msg) {
									int perf = msg.getPerformative();
									for (int p : servedPerformatives) {
										if (p == perf) {
											return true;
										}
									}
									return false;
								}
							})
						);
			}
			else {
				// No performative specified --> Match all
				template = MessageTemplate.MatchOntology(onto.getName());
			}
		}
		// Whatever template is used we avoid intercepting messages belonging to external conversations
		template  = MessageTemplate.and(template, ignoredConversations.getMessageTemplate());
	
		// Register Ontology and Language
		ContentManager cm = myAgent.getContentManager();
		if (cm.lookupOntology(onto.getName()) == null) {
			cm.registerOntology(onto);
		}
		this.codec = (codec != null ? codec : new SLCodec());
		if (cm.lookupLanguage(codec.getName()) == null) {
			cm.registerLanguage(codec);
		}
	}

	public final void action() {
		ACLMessage msg = myAgent.receive(template);
		if (msg != null) {
			if (myLogger.isLoggable(Logger.FINER)) {
				myLogger.log(Logger.FINER, "Agent "+myAgent.getName()+" - Serving incoming message "+msg);
			}
			try {
				receivedContentElement = myAgent.getContentManager().extractContent(msg);
				ContentElement keyCel = extractKeyContentElement(receivedContentElement);
						
				if (myLogger.isLoggable(Logger.FINE)) {
					myLogger.log(Logger.FINE, "Agent "+myAgent.getName()+" - Serving "+keyCel.getClass().getName()+" "+ACLMessage.getPerformative(msg.getPerformative()));
				}
				Method m = findServerMethod(keyCel, msg.getPerformative());
				if (m != null) {
					try {
						m.invoke(serverDelegate, new Object[]{keyCel, msg});
					}
					catch (InvocationTargetException ite) {
						handleServingFailure(ite.getCause(), keyCel, msg);
					}
					catch (Exception e) {
						// Should never happen as we only use public methods with proper arguments
						e.printStackTrace();
					}
				}
				else {
					handleUnsupported(keyCel, msg);
				}
			}
			catch (ContentException ce) {
				handleNotUnderstood(ce, msg);
			}
		}
		else {
			block();
		}
	}
	
	/**
	 * Allows subclasses to retrieve the actually received content element e.g. Action, Done, Result
	 * @return
	 */
	public final ContentElement getReceivedContentElement() {
		return receivedContentElement;
	}
	
	protected ContentElement extractKeyContentElement(ContentElement ce) {
		// Properly handle the SL action, done and result operators 
		if (ce instanceof Action) {
			return (AgentAction) ((Action) ce).getAction();
		}
		else if (ce instanceof Done) {
			AgentAction act = (AgentAction) ((Done) ce).getAction();
			if (act instanceof Action) {
				return (AgentAction) ((Action) act).getAction();
			}
			else {
				return act;
			}
		}
		else if (ce instanceof Result) {
			AgentAction act = (AgentAction) ((Result) ce).getAction();
			if (act instanceof Action) {
				return (AgentAction) ((Action) act).getAction();
			}
			else {
				return act;
			}
		}
		else {
			return ce; 
		}
	}
	
	protected void handleUnsupported(ContentElement keyCel, ACLMessage msg) {
		myLogger.log(Logger.WARNING, "Agent "+myAgent.getName()+" - Unsupported content-element "+keyCel.getClass().getName()+". Sender is "+msg.getSender().getName());
		ACLMessage reply = msg.createReply();
		reply.setPerformative(ACLMessage.REFUSE);
		reply.setContent("(("+ExceptionVocabulary.UNSUPPORTEDACT+" "+keyCel.getClass().getName()+"))");
		myAgent.send(reply);
	}
	
	protected void handleServingFailure(Throwable t, ContentElement cel, ACLMessage msg) {
		myLogger.log(Logger.SEVERE, "Agent "+myAgent.getName()+" - Unexpected error serving content-element "+cel.getClass().getName()+". Sender is "+msg.getSender().getName(), t);
		ACLMessage reply = msg.createReply();
		reply.setPerformative(ACLMessage.FAILURE);
		reply.setContent("(("+ExceptionVocabulary.INTERNALERROR+" \""+t+"\"))");
		myAgent.send(reply);
	}
	
	protected void handleNotUnderstood(ContentException ce, ACLMessage msg) {
		myLogger.log(Logger.WARNING, "Agent "+myAgent.getName()+" - Error decoding "+ACLMessage.getPerformative(msg.getPerformative())+" message. Sender is "+msg.getSender().getName(), ce);
		ACLMessage reply = msg.createReply();
		reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
		myAgent.send(reply);
	}
	
	private Method findServerMethod(ContentElement cel, int performative) {
		Class c = cel.getClass();
		String performativeName = performativeNames[performative];
		String key = c.getSimpleName()+performativeName;
		Method m = (Method) cachedMethods.get(key);
		if (m != null) {
			// Cache hit!
			return m;
		}
		// Note that we may have received a ContentElement that extends another one --> Possibly there is no serving 
		// method for the received ContentElement, but there is one for the parent ContentElement 
		while (!c.equals(Object.class)) {
			String methodName = "serve"+c.getSimpleName()+performativeName;
			Class[] methodParamTypes = new Class[]{c, ACLMessage.class};
			try {
				m = serverDelegate.getClass().getMethod(methodName, methodParamTypes);
				cachedMethods.put(key, m);
				break;
			}
			catch (NoSuchMethodException nsme) {
				// Try with the ContentElement superclass
				c = c.getSuperclass();
			}
		}
		return m;
	}
	
	public void ignoreConversation(String convId) {
		ignoredConversations.registerConversation(convId);
	}
	
	public void conversationFinished(String convId) {
		ignoredConversations.deregisterConversation(convId);
	}
}
