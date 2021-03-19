package io.kestra.core.models.hierarchies;

import lombok.Getter;
import io.kestra.core.models.tasks.Task;

import java.util.List;

@Getter
public class GraphClusterEnd extends GraphTask {
    public GraphClusterEnd() {
        super();
    }

    public GraphClusterEnd(GraphCluster graphCluster) {
        super(graphCluster.getTask(), graphCluster.getTaskRun(), graphCluster.getValues(), graphCluster.getRelationType());
    }

    public String getUid() {
        String uid = super.getUid();

        return (!uid.equals("") ? uid + "_" : "") + this.uid + "_end";
    }
}
