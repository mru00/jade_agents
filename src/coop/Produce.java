/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;

/**
 * Produce Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class Produce implements AgentAction {

    String resource;
    int count;

    public Produce() {
    }

    public Produce(String resource, int count) {
        this.resource = resource;
        this.count = count;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String arg) {
        resource = arg;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int arg) {
        count = arg;
    }
}
