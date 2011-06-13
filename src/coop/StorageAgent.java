package src.coop;

import jade.domain.FIPAException;
import java.util.*;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.core.Agent;
import jade.core.behaviours.*;

import jade.content.onto.Ontology;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;
import java.util.Map;
import jade.content.lang.xml.*;

import jade.content.lang.Codec.CodecException;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.util.leap.List;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class StorageAgent extends Agent {

    private Map<String, Integer> storage;
    // queues for pending resource requests
    private Map<RequestResource, AID> requestSenders;
    private ArrayList<RequestResource> requestQueue;
    private Codec codec = new XMLCodec();
    private Ontology ontology = BiFabOntology.getInstance();
    private java.util.List<String> guiLog = new java.util.LinkedList<String>();
    Behaviour beh = new CyclicBehaviour(this) {

        @Override
        public void action() {

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchLanguage(codec.getName()),
                    MessageTemplate.MatchOntology(ontology.getName()));
            ACLMessage msg = receive();


            if (msg != null) {

                try {
                    ContentElement ce = null;

                    if (msg.getPerformative() == ACLMessage.QUERY_IF) {
                        // Let JADE convert from String to Java objects
                        AID sender = msg.getSender();
                        ce = getContentManager().extractContent(msg);
                        // Worplace requests a reosurce
                        if (ce instanceof RequestResource) {
                            RequestResource p = (RequestResource) ce;
                            log("StorageAgent got an request for: "
                                    + p.getResource() + " " + p.getCount());
                            queueRequest(p, sender);
                            if (!inStorage(p)) {
                                requestProduction(p);
                            } else {
                                // process this request
                                processQueue();
                            }
                            // The LogisticAgent delivers an item
                            // from a WorplaceAgent to the Storage
                        } else if (ce instanceof Delivered) {
                            Delivered deliver = (Delivered) ce;

                            log("Storage received "
                                    + deliver.getResource() + "/"
                                    + deliver.getCount());

                            addToStorage(deliver);
                            // new items in storage, so queued request
                            // could be fullfilled and sent to workplaces
                            processQueue();
                            // A Workplace Agent tells the StorageAgent,
                            // that a product is ready to be picked up.
                        } else if (ce instanceof SetCompleted) {
                            System.out.println("StorageAgent received setcompleted");
                            SetCompleted sc = (SetCompleted) ce;
                            pickUp(sc, sender);
                        } else {
                            System.out.println("StorageAgent received unexpected, "
                                    + ce.toString());
                        }
                    } else {
                        System.out.println("StorageAgent received non-INFORM");
                    }
                } catch (CodecException ce) {
                    ce.printStackTrace();
                } catch (OntologyException oe) {
                    oe.printStackTrace();
                }
            } else {
                block();
            }

        }
    };
    private StorageAgentGUI gui;

    private void pickUp(SetCompleted sc, AID workplace) throws CodecException, OntologyException {
        Deliver deliver = new Deliver(sc.getResource(), workplace,
                new AID("Storage", false),
                1);

        ACLMessage pickUpMsg = new ACLMessage(ACLMessage.QUERY_IF);
        pickUpMsg.addReceiver(pickLogisticAID());
        pickUpMsg.setLanguage(codec.getName());
        pickUpMsg.setOntology(ontology.getName());
        getContentManager().fillContent(pickUpMsg, deliver);
        send(pickUpMsg);
    }

    private void requestProduction(RequestResource p) throws CodecException, OntologyException {
        RequestProduction request = new RequestProduction(p.getResource(), 0);
        // request the missing number of items from MonitorAgent
        int gotN = 0;
        int needN = p.getCount();
        if (storage.containsKey(p.getResource())) {
            gotN = storage.get(p.getResource());
        }
        request.setCount(needN - gotN);

        ACLMessage requestMsg = new ACLMessage(ACLMessage.QUERY_IF);
        requestMsg.addReceiver(new AID("Monitor@bifab", true));
        requestMsg.setLanguage(codec.getName());
        requestMsg.setOntology(ontology.getName());
        getContentManager().fillContent(requestMsg, request);
        send(requestMsg);
    }

    private void queueRequest(RequestResource p, AID sender) {
        requestQueue.add(p);
        requestSenders.put(p, sender);
    }

    private void processQueue() throws CodecException, OntologyException {

        ArrayList<RequestResource> toDelete = new ArrayList<RequestResource>();

        for (Iterator it = requestQueue.iterator(); it.hasNext();) {
            RequestResource p = (RequestResource) it.next();



            if (inStorage(p)) {
                // deliver
                Deliver deliver = new Deliver(p.getResource(),
                        new AID("Storage", false),
                        p.getRequester(),
                        p.getCount());


                AID logisticAgent = pickLogisticAID();

                log("Storage assigns delivery " + p.getResource()
                        + " to " + logisticAgent.getLocalName() + " for delivery to "
                        + p.getRequester().getLocalName());


                ACLMessage deliverMsg = new ACLMessage(ACLMessage.QUERY_IF);
                deliverMsg.addReceiver(logisticAgent);
                deliverMsg.setLanguage(codec.getName());
                deliverMsg.setOntology(ontology.getName());
                getContentManager().fillContent(deliverMsg, deliver);
                send(deliverMsg);

                // remove
                takeFromStorage(p);
                requestSenders.remove(p);
                toDelete.add(p);
            }


        }

        for (RequestResource p : toDelete) {
            requestQueue.remove(p);
        }
    }

    private AID pickLogisticAID() {
        try {
            DFAgentDescription tmpl = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Logistics");
            tmpl.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, tmpl);
            Random r = new Random();
            return result[r.nextInt(result.length)].getName();
        } catch (FIPAException ex) {
            Logger.getLogger(StorageAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private boolean inStorage(RequestResource p) {
        if (storage.containsKey(p.getResource())) {
            int needN = p.getCount();
            int gotN = storage.get(p.getResource());
            return (needN <= gotN);
        } else {
            return false;
        }
    }

    private void takeFromStorage(RequestResource p) {
        if (inStorage(p)) {
            int needN = p.getCount();
            storage.put(p.getResource(), storage.get(p.getResource()) - p.getCount());
        }
    }

    private void addToStorage(Delivered d) {


        int gotN = 0;
        if (storage.containsKey(d.getResource())) {
            gotN = storage.get(d.getResource());
        }
        storage.put(d.getResource(), gotN + d.getCount());


        dumpStorage();

    }

    private void dumpStorage() {
        Set<String> keys = storage.keySet();

        System.out.println("StorageMananger: current storage");
        for (Iterator it = keys.iterator(); it.hasNext();) {
            String entry = (String) it.next();
            System.out.println("\tStorageManager: " + entry + "\t\t" + storage.get(entry));


            gui.setStorageCount(entry, storage.get(entry));


            int req = 0;

            for (Iterator itReq = requestQueue.iterator(); itReq.hasNext();) {
                RequestResource p = (RequestResource) itReq.next();
                if (p.getResource().equals(entry)) {
                    req += p.getCount();
                }

            }

            gui.setRequestedCount(entry, req);
        }
    }

    private void log(String msg) {
        System.out.println(msg);
        gui.addLog(msg);
    }

    @Override
    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        gui = new StorageAgentGUI();
        gui.setVisible(true);

        storage = new HashMap<String, Integer>();
        requestSenders = new HashMap<RequestResource, AID>();
        requestQueue = new ArrayList<RequestResource>();

        addBehaviour(beh);
    }

    @Override
    protected void takeDown() {
        System.out.println("StorageAgent " + getAID().getName() + "exits.");
    }
}
