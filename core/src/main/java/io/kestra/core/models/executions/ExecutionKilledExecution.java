package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.runners.WorkerTask;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ExecutionKilledExecution extends ExecutionKilled implements TenantInterface, AsyncOperation {
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
     * The state to move the execution to after kill.
     */
    io.kestra.core.models.flows.State.Type executionState;

    /**
     * Specifies whether killing the execution, also kill all sub-flow executions.
     */
    Boolean isOnKillCascade;

    /**
     * Optional correlation id for the async operation that triggered this kill.
     */
    @Nullable
    String operationId;

    public boolean isEqual(WorkerTask workerTask) {
        String taskTenantId = workerTask.getTaskRun().getTenantId();
        String taskExecutionId = workerTask.getTaskRun().getExecutionId();
        return (taskTenantId == null || taskTenantId.equals(this.tenantId)) && taskExecutionId.equals(this.executionId);
    }

    @Override
    public String uid() {
        return this.executionId;
    }

    @Override
    public String operationId() {
        return this.operationId;
    }
}
