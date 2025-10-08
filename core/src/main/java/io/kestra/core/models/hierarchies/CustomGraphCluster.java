package io.kestra.core.models.hierarchies;

import lombok.Getter;

import java.util.List;

@SuppressWarnings("this-escape")
@Getter
public class CustomGraphCluster extends GraphCluster {
    public CustomGraphCluster(String uid, GraphTask rootTask, List<CustomGraphNode> nodes) {
        super(rootTask, uid, RelationType.SEQUENTIAL); // TODO should we add a custom relation type?

        this.getGraph().addNode(rootTask);
        this.addEdge(this.getRoot(), rootTask, new Relation());

        this.getGraph().removeEdge(rootTask, this.getFinally());
        this.getGraph().removeEdge(rootTask, this.getAfterExecution());
        this.getGraph().removeNode(this.getFinally());
        this.getGraph().removeNode(this.getAfterExecution());

        nodes.forEach(node -> {
            this.getGraph().addNode(node);
            this.addEdge(rootTask, node, new Relation(RelationType.SEQUENTIAL, null));
            this.addEdge(node, this.getEnd(), new Relation());
        });
    }

    @Override
    public void updateUidWithChildren(String uid) {
        // as children are not "regular children with parent -> child relationship as with flowable task"
        // we fall back to the existing UID.
        this.uid = uid;
    }
}