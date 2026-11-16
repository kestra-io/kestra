package io.kestra.core.assets;

import java.time.Instant;
import java.util.Map;

import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTask;

import io.micronaut.core.annotation.Nullable;

/**
 * The attempt of a task run on whose behalf assets are emitted.
 *
 * @param attemptStartDate when the attempt started, the same instant for every asset the attempt uses so its usage is recorded once
 */
public record AssetEmitContext(String tenantId, String namespace, String flowId, Integer flowRevision, String executionId, String taskId, String taskRunId,
    String taskType, @Nullable ExecutionKind executionKind, Instant attemptStartDate) {

    @SuppressWarnings("unchecked")
    public static AssetEmitContext of(WorkerTask workerTask, Map<String, Object> variables) {
        TaskRun taskRun = workerTask.getTaskRun();
        Integer flowRevision = variables.get("flow") instanceof Map<?, ?> flow && ((Map<String, Object>) flow).get("revision") instanceof Number revision
            ? revision.intValue()
            : null;
        return new AssetEmitContext(
            taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), flowRevision, taskRun.getExecutionId(), taskRun.getTaskId(), taskRun.getId(),
            workerTask.getTask().getType(), workerTask.getExecutionKind(), Instant.now()
        );
    }

    public AssetUser assetUser(State.Type state, @Nullable Instant endDate) {
        return new AssetUser(tenantId, namespace, flowId, flowRevision, executionId, taskId, taskRunId, state, attemptStartDate, endDate);
    }
}
