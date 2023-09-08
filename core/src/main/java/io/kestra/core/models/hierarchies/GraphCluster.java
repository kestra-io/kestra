package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;

import java.util.List;
import java.util.Optional;


@Getter
public class GraphCluster extends AbstractGraph {
    @JsonIgnore
    protected final Graph<AbstractGraph, Relation> graph = new Graph<>();

    protected RelationType relationType;

    @JsonIgnore
    protected final GraphClusterRoot root;

    @JsonIgnore
    protected final GraphClusterEnd end;

    protected final AbstractGraphTask taskNode;

    public GraphCluster() {
        this("root");
    }


    public GraphCluster(String uid) {
        super(uid);

        this.relationType = null;
        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();
        this.taskNode = null;

        this.addNode(this.root);
        this.addNode(this.end);
    }

    public GraphCluster(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        this(new GraphTask(task.getId(), task, taskRun, values, relationType), task.getId(), relationType);

        this.addNode(this.taskNode, false);
        this.addEdge(this.getRoot(), this.taskNode, new Relation());
    }

    protected GraphCluster(AbstractGraphTask taskNode, String uid, RelationType relationType) {
        super(uid);

        this.relationType = relationType;
        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();
        this.taskNode = taskNode;

        this.addNode(this.root);
        this.addNode(this.end);
    }

    public void addNode(AbstractGraph node) {
        this.addNode(node, true);
    }

    public void addNode(AbstractGraph node, boolean withClusterUidPrefix) {
        if (withClusterUidPrefix) {
            node.setUid(prefixedUid(node.uid));
        }
        this.getGraph().addNode(node);
    }

    public void addEdge(AbstractGraph source, AbstractGraph target, Relation relation) {
        this.getGraph().addEdge(source, target, relation);
    }

    private String prefixedUid(String uid) {
        return Optional.ofNullable(this.uid).map(u -> u + "." + uid).orElse(uid);
    }

    @Override
    public String getUid() {
        return "cluster_" + super.getUid();
    }

    @Override
    public void setUid(String uid) {
        graph.nodes().stream().filter(node -> (!(node instanceof GraphClusterRoot) && !(node instanceof GraphClusterEnd))
                || node.equals(this.root) || node.equals(this.end))
            .forEach(node -> node.setUid(uid + node.uid.substring(this.uid.length())));

        super.setUid(uid);
    }

    @Override
    public void setError(boolean error) {
        this.error = error;

        this.taskNode.error = error;
        this.root.error = error;
        this.end.error = error;
    }
}