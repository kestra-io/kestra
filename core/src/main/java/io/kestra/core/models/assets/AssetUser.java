package io.kestra.core.models.assets;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;

import java.time.Instant;

/**
 * Represents an entity that used an asset
 */
public record AssetUser(String tenantId, String namespace, String flowId, Integer flowRevision, String executionId, String taskId, String taskRunId,
                        State.Type state, Instant startDate, Instant endDate) implements HasUID {
    public String uid() {
        return IdUtils.fromParts(tenantId, namespace, flowId, String.valueOf(flowRevision), executionId, taskRunId);
    }

    public FlowId toFlowId() {
        return FlowId.of(tenantId, namespace, flowId, flowRevision);
    }
}
