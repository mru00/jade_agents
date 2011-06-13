/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;
import jade.core.AID;

/**
 * RequestResource Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class RequestResource implements AgentAction {

    private String resource;
    private Integer count;
    private AID requester;

    public RequestResource(String resource, Integer count) {
        this.resource = resource;
        this.count = count;
    }

    public RequestResource() {
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public AID getRequester() {
        return requester;
    }

    public void setRequester(AID requester) {
        this.requester = requester;
    }
}
