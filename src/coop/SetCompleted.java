/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;

/**
 * SetCompleted Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class SetCompleted implements AgentAction {

    String resource;

    public SetCompleted() {
    }

    public SetCompleted(String resource) {
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String arg) {
        resource = arg;
    }
}
