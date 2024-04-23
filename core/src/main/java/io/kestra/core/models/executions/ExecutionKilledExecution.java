package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.WorkerTask;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * The Kestra event for killing an execution. A {@link ExecutionKilledExecution} can be in two states:
 * <p>
 * <pre>
 *  - {@link State#REQUESTED}: The event was requested either by an Executor or by an external request.
 *  - {@link State#EXECUTED}: The event was consumed and processed by the Executor.
 *  </pre>
 *
 *  A {@link ExecutionKilledExecution} will always transit from {@link State#REQUESTED} to {@link State#EXECUTED}
 *  regardless of whether the associated execution exist or not to ensure that Workers will be notified for the tasks
 *  to be killed no matter what the circumstances.
 *  <p>
 *  IMPORTANT: A {@link ExecutionKilledExecution} is considered to be a fire-and-forget event. As a result, we do not manage a
 *  COMPLETED state, i.e., the Executor will never wait for Workers to process an executed {@link ExecutionKilledExecution}
 *  before considering an execution to be KILLED.
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ExecutionKilledExecution extends ExecutionKilled implements TenantInterface {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "execution";

    /**
     * The execution to be killed.
     */
    @NotNull
    String executionId;

    /**
     * Specifies whether killing the execution, also kill all sub-flow executions.
     */
    Boolean isOnKillCascade;

    public boolean isEqual(WorkerTask workerTask) {
        return (workerTask.getTaskRun().getTenantId() == null || (workerTask.getTaskRun().getTenantId() != null && workerTask.getTaskRun().getTenantId().equals(this.tenantId))) &&
            workerTask.getTaskRun().getExecutionId().equals(this.executionId);
    }

    @Override
    public String uid() {
        return this.executionId;
    }
}
