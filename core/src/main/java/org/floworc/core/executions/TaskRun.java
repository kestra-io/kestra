package org.floworc.core.executions;

import com.devskiller.friendly_id.FriendlyId;
import lombok.Builder;
import lombok.Value;
import org.floworc.core.flows.State;
import org.floworc.core.tasks.Task;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Value
@Builder
public class TaskRun {
    private String id;

    @NotNull
    private String executionId;

    @NotNull
    private String flowId;

    @NotNull
    private String taskId;

    private Context context;

    @Builder.Default
    private List<LogEntry> logs = new ArrayList<>();

    @Builder.Default
    private List<MetricEntry> metrics = new ArrayList<>();

    @NotNull
    private State state;

    public TaskRun withState(State.Type state) {
        return new TaskRun(
            this.id,
            this.executionId,
            this.flowId,
            this.taskId,
            this.context,
            this.logs,
            this.metrics,
            this.state.withState(state)
        );
    }

    public static TaskRun of(Execution execution, Task task) {
        return TaskRun.builder()
            .id(FriendlyId.createFriendlyId())
            .executionId(execution.getId())
            .flowId(execution.getFlowId())
            .taskId(task.getId())
            .state(new State())
            .build();
    }

}
