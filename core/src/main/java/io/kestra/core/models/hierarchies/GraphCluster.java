package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Getter
public class GraphCluster extends AbstractGraph {
    @JsonIgnore
    private final Graph<AbstractGraph, Relation> graph = new Graph<>();

    private final RelationType relationType;

    @JsonIgnore
    private final GraphClusterRoot root;

    @JsonIgnore
    private final GraphClusterEnd end;

    private final AbstractGraphTask taskNode;

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
            node.updateUidWithChildren(prefixedUid(node.uid));
        }
        this.getGraph().addNode(node);
    }

    public void addEdge(AbstractGraph source, AbstractGraph target, Relation relation) {
        this.getGraph().addEdge(source, target, relation);
    }

    private String prefixedUid(String uid) {
        return Optional.ofNullable(this.uid).map(u -> u + "." + uid).orElse(uid);
    }

    public Map<GraphCluster, List<AbstractGraph>> allNodesByParent() {
        Map<Boolean, List<AbstractGraph>> nodesByIsCluster = this.graph.nodes().stream().collect(Collectors.partitioningBy(n -> n instanceof GraphCluster));

        Map<GraphCluster, List<AbstractGraph>> nodesByParent = new HashMap<>(Map.of(
            this,
            nodesByIsCluster.get(false)
        ));

        nodesByIsCluster.get(true).forEach(n -> {
            GraphCluster cluster = (GraphCluster) n;
            nodesByParent.putAll(cluster.allNodesByParent());
        });

        return nodesByParent;
    }

    @Override
    public String getUid() {
        return "cluster_" + super.getUid().replace("cluster_", "");
    }

    @Override
    public void updateUidWithChildren(String uid) {
        graph.nodes().stream().filter(node ->
                // filter other clusters' root & end to prevent setting uid multiple times
                // this is because we need other clusters' root & end to have edges over them, but they are already managed by their own cluster
                (!(node instanceof GraphClusterRoot) && !(node instanceof GraphClusterEnd))
                || node.equals(this.root) || node.equals(this.end))
            .forEach(node -> node.updateUidWithChildren(uid + node.uid.substring(this.uid.length())));

        super.updateUidWithChildren(uid);
    }

    @Override
    public void updateErrorWithChildren(boolean error) {
        this.error = error;

        this.taskNode.error = error;
        this.root.error = error;
        this.end.error = error;
    }
}