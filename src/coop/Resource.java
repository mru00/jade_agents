/*
 * Cooperative Systems
 */
package src.coop;

import jade.content.Concept;

/**
 * Resource Ontology Java Class
 * @author Rudolf Mühlbauer, Aurel Wildfellner
 */
public class Resource implements Concept {

    private String id;
    private Integer count;

    public Resource() {
    }

    public Resource(String name, Integer count) {
        this.id = name;
        this.count = count;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
