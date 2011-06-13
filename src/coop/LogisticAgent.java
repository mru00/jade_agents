/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.xml.*;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;

import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;

/**
 * Implementation of the logistics Agent
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class LogisticAgent extends Agent {

    private Codec codec = new XMLCodec();
    private Ontology ontology = BiFabOntology.getInstance();
    // message behaviour
    Behaviour messageBeh = new CyclicBehaviour(this) {

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
                        ce = getContentManager().extractContent(msg);
                        if (ce instanceof Deliver) {
                            // delivers immediatly
                            onDeliver((Deliver) ce);
                        } else {
                            System.out.println("LogisticAgent " + getLocalName()
                                    + " received unexpected, " + ce.toString());
                        }
                    } else {
                        System.out.println("LogisticAgent " + getLocalName()
                                + " received non-INFORM");
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

    private void onDeliver(Deliver ce) throws CodecException, OntologyException {
        System.out.println("LogisticAgent " + getLocalName()
                + " sending " + ce.getResource() + "/" + ce.count
                + " from " + ce.getSource().getLocalName()
                + " to: " + ce.dest.getLocalName());

        // this message is received from the storage when something is to be
        // delivered
        Delivered delivered = new Delivered(ce.resource, ce.count);
        ACLMessage requestMsg = new ACLMessage(ACLMessage.QUERY_IF);
        requestMsg.addReceiver(ce.dest);
        requestMsg.setLanguage(codec.getName());
        requestMsg.setOntology(ontology.getName());
        getContentManager().fillContent(requestMsg, delivered);
        send(requestMsg);
    }

    @Override
    protected void setup() {
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);


        //Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Logistics");
        sd.setName(getLocalName() + "Logistics");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(messageBeh);
    }

    @Override
    protected void takeDown() {
        System.out.println("Logistic Agent " + getAID().getName() + "exits.");
        //Deregister from yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
