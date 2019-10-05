package org.floworc.core.models.executions;

import com.devskiller.friendly_id.FriendlyId;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

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

    @Wither
    private List<LogEntry> logs;

    @Wither
    private Map<String, Object> outputs;

    @Wither
    private List<MetricEntry> metrics;

    @NotNull
    private State state;

    public TaskRun withState(State.Type state) {
        return new TaskRun(
            this.id,
            this.executionId,
            this.flowId,
            this.taskId,
            this.logs,
            this.outputs,
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
