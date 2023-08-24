package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;

import java.util.List;


@Getter
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


    public GraphCluster(String uid) {
        super(uid);

        this.relationType = null;
        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();

        graph.addNode(this.root);
        graph.addNode(this.end);
    }

    public GraphCluster(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super();

        this.uid = "cluster_" + task.getId();
        this.relationType = relationType;

        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();

        graph.addNode(this.root);
        graph.addNode(this.end);

        GraphTask flowableGraphTask = new GraphTask(task, taskRun, values, relationType);
        this.getGraph().addNode(flowableGraphTask);
        this.getGraph().addEdge(this.getRoot(), flowableGraphTask, new Relation());
    }
}
