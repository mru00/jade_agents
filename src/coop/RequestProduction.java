/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;
import jade.core.AID;

/**
 * RequestProduction Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class RequestProduction implements AgentAction {

    private String resource;
    private int count;

    public RequestProduction() {
    }

    public RequestProduction(String resource, int count) {
        this.resource = resource;
        this.count = count;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
