/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.lang.xml.*;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.leap.List;
import jade.util.leap.ArrayList;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import java.awt.event.ActionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;

/**
 * Implementation of the monitor Agent
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class MonitorAgent extends Agent {

    private MonitorAgentGUI gui = new MonitorAgentGUI();
    private Codec codec = new XMLCodec();
    private Ontology ontology = BiFabOntology.getInstance();
    // holds all production plans: which parts are needed for which product
    Map<String, List> constructionPlans;
    // message handling behaviour
    Behaviour readMessages = new CyclicBehaviour(this) {

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
                        if (ce instanceof RequestProduction) {

                            // requestproduction is received from the StorageAgent
                            // when something is not available.
                            onRequestProduction((RequestProduction) ce);
                        } else {
                            System.out.println("MonitorAgent received unexpected, "
                                    + ce.toString());
                        }
                    } else {
                        System.out.println("MonitorAgent received non-INFORM" + msg);
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
    private ProduceActionListener onProduce = new ProduceActionListener() {

        public void actionPerformed(String product, int count) {
            try {
                RequestProduction request = new RequestProduction(product, count);


                ACLMessage requestMsg = new ACLMessage(ACLMessage.QUERY_IF);
                requestMsg.addReceiver(new AID("Monitor@bifab", true));
                requestMsg.setLanguage(codec.getName());
                requestMsg.setOntology(ontology.getName());
                getContentManager().fillContent(requestMsg, request);
                send(requestMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void setup() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Logger.getLogger(MonitorAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        // this is a sample construction plan for a bicycle.
        //
        constructionPlans = new HashMap<String, List>();

        List fahrradResources = new ArrayList();
        fahrradResources.add(new Resource("reifen", 2));
        fahrradResources.add(new Resource("rahmen", 1));
        fahrradResources.add(new Resource("sattel", 1));
        fahrradResources.add(new Resource("bremsen", 2));
        fahrradResources.add(new Resource("schaltung", 1));

        List reifenResources = new ArrayList();
        reifenResources.add(new Resource("nabe", 1));
        reifenResources.add(new Resource("speiche", 20));

        // register construction plans
        constructionPlans.put("fahrrad", fahrradResources);
        constructionPlans.put("reifen", reifenResources);

        for (String plan : constructionPlans.keySet()) {
            gui.addPlan(plan, constructionPlans.get(plan));
        }


        // start initial production: toplevel
        try {
            //assignProductionPlan("fahrrad", 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        addBehaviour(readMessages);

        gui.setVisible(true);
        gui.addProduceListener(onProduce);
    }

    @Override
    protected void takeDown() {
        System.out.println("MonitorAgent " + getAID().getName() + "exits.");
    }

    private void onRequestProduction(RequestProduction ce) throws CodecException, OntologyException {

        // when the storage needs something, it asks the monitor for it.
        // the monitor decides if it will be build by an workplace
        // or bought from external sources

        String id = ce.getResource();
        int deliveredCount = ce.getCount();

        System.out.println("MonitorAgent got an requestProduction for: "
                + ce.getResource() + "/" + deliveredCount);

        assignProductionPlan(id, deliveredCount);
    }

    private void assignProductionPlan(String what, int count) {

        class AssignProductionPlan extends OneShotBehaviour {

            String what;
            int count;
            AID where;

            public AssignProductionPlan(Agent a, String what, AID where, int count) {
                super(a);
                this.what = what;
                this.where = where;
                this.count = count;
            }

            @Override
            public void action() {
                try {
                    System.out.println("MonitorAgent is assigning productionplan: "
                            + what + " to " + where.getName());
                    ProductionPlan p = new ProductionPlan(what,
                            constructionPlans.get(what),
                            count);
                    ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
                    msg.addReceiver(where);
                    msg.setLanguage(codec.getName());
                    msg.setOntology(ontology.getName());
                    getContentManager().fillContent(msg, p);
                    send(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


        System.out.println("ASSIGN PRODUCTION");
        AID workplace = null;
        try {
            SearchConstraints onlyOne = new SearchConstraints();
            onlyOne.setMaxResults(new Long(1));

            DFAgentDescription tmpl = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Workplace");
            sd.addProperties(new Property("Product", what));
            tmpl.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, tmpl, onlyOne);

            System.out.println("search resulted in: " + result);
            for (DFAgentDescription da : result) {
                System.out.println("found: for " + what + ":" + da.getName());
            }

            if (result.length == 1) {
                // we found a workplace that can produce the item
                workplace = result[0].getName();
            } else {
                // no workplace can produce this item - buy it!
                workplace = new AID("WorkplacePurchase", false);
            }


            // send a new productionplan to a workplace
            addBehaviour(new AssignProductionPlan(this, what, workplace, count));

        } catch (FIPAException ex) {
            ex.printStackTrace();
            Logger.getLogger(StorageAgent.class.getName()).log(Level.SEVERE, null, ex);
        }




    }
}
