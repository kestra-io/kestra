package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class GraphCluster extends AbstractGraph {
    @JsonIgnore
    protected final Graph<AbstractGraph, Relation> graph = new Graph<>();

    protected RelationType relationType;

    @JsonIgnore
    private final GraphClusterRoot root;

    @JsonIgnore
    private final GraphClusterEnd end;

    public GraphCluster() {
        super();

        this.relationType = null;
        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();

        graph.addNode(this.root);
        graph.addNode(this.end);
    }

    public GraphCluster(RelationType relationType) {
        super();

        this.relationType = relationType;
        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();

        graph.addNode(this.root);
        graph.addNode(this.end);
    }

    public GraphCluster(RelationType relationType, GraphClusterRoot root, GraphClusterEnd end) {
        super();

        this.relationType = relationType;

        if(root != null){
            graph.addNode(root);
        }
        if(end != null){
            graph.addNode(end);
        }

        this.root = root;
        this.end = end;
    }

    public GraphCluster(GraphClusterRoot root, GraphClusterEnd end) {
        super();

        this.relationType = null;

        if(root != null){
            graph.addNode(root);
        }
        if(end != null){
            graph.addNode(end);
        }

        this.root = root;
        this.end = end;
    }

}
