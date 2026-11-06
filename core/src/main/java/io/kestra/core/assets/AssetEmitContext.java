package io.kestra.core.assets;

import java.time.Instant;

import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.State;

import io.micronaut.core.annotation.Nullable;

/**
 * The task run on whose behalf assets are emitted.
 */
public record AssetEmitContext(String tenantId, String namespace, String flowId, Integer flowRevision, String executionId, String taskId, String taskRunId,
    String taskType, @Nullable ExecutionKind executionKind) {

    public AssetUser assetUser(State.Type state, Instant startDate, @Nullable Instant endDate) {
        return new AssetUser(tenantId, namespace, flowId, flowRevision, executionId, taskId, taskRunId, state, startDate, endDate);
    }
}
