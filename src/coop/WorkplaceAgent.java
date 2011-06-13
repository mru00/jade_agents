/*
 * Cooperative Systems
 */
package src.coop;

import jade.domain.FIPAAgentManagement.Property;
import jade.content.lang.Codec;
import jade.content.lang.xml.*;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.content.lang.Codec.CodecException;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.leap.List;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the Workplace Agent
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class WorkplaceAgent extends Agent {

    // which resources were already delivered to the workplace?
    private Map<String, Integer> availableResources;
    // the queue of the items to produce
    private LinkedList<ProductionPlan> toProduce = null;
    // what is currently being produced
    private ProductionPlan currentProductionPlan = null;
    private Codec codec = new XMLCodec();
    private Ontology ontology = BiFabOntology.getInstance();
    // the message handling behaviour
    Behaviour readMessages = new CyclicBehaviour(this) {

        @Override
        public void action() {


            // filtering the messages also hides "FAILURE" messages,
            // disabled for debugging
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchLanguage(codec.getName()),
                    MessageTemplate.MatchOntology(ontology.getName()));

            ACLMessage msg = receive();

            if (msg != null) {

                try {
                    ContentElement ce = null;

                    // decode the message
                    if (msg.getPerformative() == ACLMessage.QUERY_IF) {

                        ce = getContentManager().extractContent(msg);
                        if (ce instanceof ProductionPlan) {

                            ProductionPlan p = (ProductionPlan) ce;
                            System.out.println("WorkplaceAgent " + getLocalName()
                                    + " received production plan for: "
                                    + p.id + "/" + p.getCount());

                            onSetProductionPlan(p);

                        } else if (ce instanceof Delivered) {
                            onDelivered((Delivered) ce);
                        } else {
                            System.out.println("WorkplaceAgent "
                                    + getLocalName() + " received unexpected, "
                                    + ce.toString());
                        }
                    } else {
                        System.out.println("WorkplaceAgent "
                                + getLocalName() + " received non-INFORM");
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

    @Override
    protected void setup() {
        toProduce = new LinkedList<ProductionPlan>();
        availableResources = new HashMap<String, Integer>();

        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        Object[] args = getArguments();
        String product = "";

        if (args != null && args.length == 1) {
            product = "" + args[0];
        } else {
            System.out.println(getLocalName() + " must have arguments");
            System.exit(1);
        }

        //Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Workplace");
        sd.setName(getLocalName() + "Workplace");
        sd.addProperties(new Property("Product", product));
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }


        System.out.println("WorkplaceAgent: created for " + product);
        addBehaviour(readMessages);
    }

    private void checkCompleted() throws CodecException, OntologyException {

        // check if the product can be produced with the current availables
        boolean complete = true;
        List resources = currentProductionPlan.getResources();

        // check if needs are satisfied
        for (int i = 0; i < resources.size(); i++) {
            Resource r = (Resource) resources.get(i);
            if (!availableResources.containsKey(r.getId())) {
                complete = false;
                break;
            }
            int availCount = availableResources.get(r.getId());
            if (r.getCount() > availCount) {
                complete = false;
                break;
            }
        }

        // debug output
        for (int i = 0; i < resources.size(); i++) {
            Resource r = (Resource) resources.get(i);
            int availCount = availableResources.get(r.getId());

            System.out.println("WorkplaceAgent " + getLocalName() + " has " + availCount + " of " + r.getId()
                    + " and needs " + r.getCount());
        }

        if (complete) {
            // update available-counts
            for (int i = 0; i < resources.size(); i++) {
                Resource r = (Resource) resources.get(i);
                int availCount = availableResources.get(r.getId());
                availableResources.put(r.getId(), availCount - r.getCount());
            }

            // inform the storage that this item is finished
            setCompleted();
        }
    }

    private void onSetProductionPlan(ProductionPlan ce) throws CodecException, OntologyException {


        // enqueue a productionplan for every "count"
        for (int i = 0; i < ce.getCount(); i++) {
            toProduce.add(ce);
        }

        // process whole productionplan
        while (!toProduce.isEmpty()) {

            currentProductionPlan = toProduce.removeLast();

            List resources = currentProductionPlan.getResources();


            if (resources == null) {
                // this is a buy-request since it does not build anything.
                // this is by convention.
                setCompleted();
            } else {

                // this is a construction request. analyze requirements
                // and produce when parts are ready.

                for (int i = 0; i < resources.size(); i++) {
                    Resource r = (Resource) resources.get(i);
                    System.out.println("WorkplaceAgent " + getLocalName()
                            + " needs: " + r.getId() + "/"
                            + r.getCount() + " from storage");

                    // initially non quantities of the resource are available
                    availableResources.put(r.getId(), 0);

                    RequestResource requestRes = new RequestResource(r.getId(), r.getCount());
                    requestRes.setRequester(getAID());

                    ACLMessage requestMsg = new ACLMessage(ACLMessage.QUERY_IF);

                    requestMsg.addReceiver(new AID("Storage", false));
                    requestMsg.setLanguage(codec.getName());
                    requestMsg.setOntology(ontology.getName());
                    getContentManager().fillContent(requestMsg, requestRes);
                    send(requestMsg);
                }

                checkCompleted();
            }
        }

    }

    private void onDelivered(Delivered ce) throws CodecException, OntologyException {

        String id = ce.getResource();
        int deliveredCount = ce.getCount();

        System.out.println("WorkplaceAgent " + getLocalName()
                + " received: " + id + "/"
                + deliveredCount + " from logistics");

        // update available resources to new counts
        if (availableResources.containsKey(id)) {
            int availCount = availableResources.get(id);
            availableResources.put(id, availCount + deliveredCount);
        }

        // see if the product can already be built
        checkCompleted();
    }

    private void setCompleted() throws CodecException, OntologyException {

        // a little delay to make simulation more pretty
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Logger.getLogger(WorkplaceAgent.class.getName()).log(Level.SEVERE, null, ex);
        }


        System.out.println("WorkplaceAgent " + getLocalName()
                + " completed: " + currentProductionPlan.id
                + " with " + toProduce.size() + " more to produce");

        // send message to storage: completed!
        SetCompleted setCompleted = new SetCompleted(currentProductionPlan.id);
        ACLMessage requestMsg = new ACLMessage(ACLMessage.QUERY_IF);
        requestMsg.addReceiver(new AID("Storage", false));
        requestMsg.setLanguage(codec.getName());
        requestMsg.setOntology(ontology.getName());
        getContentManager().fillContent(requestMsg, setCompleted);
        send(requestMsg);

    }
}
