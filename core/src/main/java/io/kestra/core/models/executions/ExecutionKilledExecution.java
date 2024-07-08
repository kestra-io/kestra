package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.WorkerTask;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
        String taskTenantId = workerTask.getTaskRun().getTenantId();
        String taskExecutionId = workerTask.getTaskRun().getExecutionId();
        return (taskTenantId == null || taskTenantId.equals(this.tenantId)) && taskExecutionId.equals(this.executionId);
    }

    @Override
    public String uid() {
        return this.executionId;
    }
}
