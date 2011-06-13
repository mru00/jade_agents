/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;
import jade.core.AID;

/**
 * Deliver Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class Deliver implements AgentAction {

    String resource;
    AID source;
    AID dest;
    Integer count;

    public Deliver() {
    }

    public Deliver(String resource, AID source, AID dest, int count) {
        this.resource = resource;
        this.source = source;
        this.dest = dest;
        this.count = count;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String arg) {
        resource = arg;
    }

    public AID getSource() {
        return source;
    }

    public void setSource(AID arg) {
        source = arg;
    }

    public AID getDest() {
        return dest;
    }

    public void setDest(AID arg) {
        dest = arg;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int arg) {
        count = arg;
    }
}
