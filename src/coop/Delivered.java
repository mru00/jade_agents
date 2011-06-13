/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;

/**
 * Delivered Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class Delivered implements AgentAction {

    String resource;
    private int count;

    public Delivered() {
    }

    public Delivered(String resource, int count) {
        this.resource = resource;
        this.count = count;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String arg) {
        resource = arg;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
