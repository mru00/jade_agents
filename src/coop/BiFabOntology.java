/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PrimitiveSchema;

/**
 *
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class BiFabOntology extends Ontology {

    private PrimitiveSchema getStringSchema() throws Throwable {
        return (PrimitiveSchema) getSchema(BasicOntology.STRING);
    }

    private ConceptSchema getAIDSchema() throws Throwable {
        return (ConceptSchema) getSchema(BasicOntology.AID);
    }

    private PrimitiveSchema getIntegerSchema() throws Throwable {
        return (PrimitiveSchema) getSchema(BasicOntology.INTEGER);
    }
    private static Ontology theInstance = new BiFabOntology();

    public static Ontology getInstance() {

        return theInstance;

    }

    public BiFabOntology() {
        super("BiFabOntology", BasicOntology.getInstance());
        try {

            // define ontology for our example

            AgentActionSchema productionPlan = new AgentActionSchema("productionplan");
            ConceptSchema resource = new ConceptSchema("resource");

            AgentActionSchema buy = new AgentActionSchema("buy");
            AgentActionSchema produce = new AgentActionSchema("produce");
            AgentActionSchema setCompleted = new AgentActionSchema("set-completed");
            AgentActionSchema requestProduction = new AgentActionSchema("request-production");
            AgentActionSchema requestResource = new AgentActionSchema("request-resource");
            AgentActionSchema deliver = new AgentActionSchema("deliver");
            AgentActionSchema delivered = new AgentActionSchema("delivered");


            add(resource, Resource.class);
            add(buy, Buy.class);
            add(produce, Produce.class);
            add(setCompleted, SetCompleted.class);
            add(requestProduction, RequestProduction.class);
            add(requestResource, RequestResource.class);
            add(delivered, Delivered.class);
            add(deliver, Deliver.class);

            add(productionPlan, ProductionPlan.class);

            resource.add("id", getStringSchema());
            resource.add("count", getIntegerSchema());

            productionPlan.add("id", getStringSchema());
            productionPlan.add("count", getIntegerSchema());
            productionPlan.add("resources", resource, 0, ObjectSchema.UNLIMITED);

            buy.add("resource", getStringSchema());
            buy.add("count", getIntegerSchema());

            produce.add("resource", getStringSchema());
            produce.add("count", getIntegerSchema());

            setCompleted.add("resource", getStringSchema());

            requestProduction.add("resource", getStringSchema());
            requestProduction.add("count", getIntegerSchema());

            requestResource.add("resource", getStringSchema());
            requestResource.add("count", getIntegerSchema());
            requestResource.add("requester", getAIDSchema());

            delivered.add("resource", getStringSchema());
            delivered.add("count", getIntegerSchema());

            deliver.add("resource", getStringSchema());
            deliver.add("source", getAIDSchema());
            deliver.add("dest", getAIDSchema());
            deliver.add("count", getIntegerSchema());

        } catch (OntologyException oe) {
            oe.printStackTrace();

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
