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

}
