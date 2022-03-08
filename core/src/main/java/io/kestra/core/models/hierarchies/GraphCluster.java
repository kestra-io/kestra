package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import java.util.List;

@Getter
public class GraphCluster extends AbstractGraphTask {
    @JsonIgnore
    private final Graph<AbstractGraphTask, Relation> graph = new Graph<>();

    @JsonIgnore
    private final GraphClusterRoot root;

    @JsonIgnore
    private final GraphClusterEnd end;

    public GraphCluster() {
        super();

        this.root = new GraphClusterRoot();
        this.end = new GraphClusterEnd();

        graph.addNode(this.root);
        graph.addNode(this.end);
    }

    public GraphCluster(Task task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(task, taskRun, values, relationType);
        this.root = new GraphClusterRoot(this);
        this.end = new GraphClusterEnd(this);

        graph.addNode(this.root);
        graph.addNode(this.end);
    }
}
