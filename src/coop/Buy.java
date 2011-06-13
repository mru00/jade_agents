/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;

/**
 * Buy Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class Buy implements AgentAction {

    String resource;

    public Buy() {
    }

    public Buy(String resource) {
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String arg) {
        resource = arg;
    }
}
