/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.AgentAction;
import jade.util.leap.List;

/**
 * ProductionPlan Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class ProductionPlan implements AgentAction {

    String id;
    private List resources;
    private int count;

    public ProductionPlan() {
    }

    public ProductionPlan(String name, List resources, int count) {
        this.id = name;
        this.resources = resources;
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List getResources() {
        return resources;
    }

    public void setResources(List resources) {
        this.resources = resources;
    }


    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
