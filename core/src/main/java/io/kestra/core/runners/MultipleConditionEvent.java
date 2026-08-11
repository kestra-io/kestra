package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.IdUtils;

public record MultipleConditionEvent(Flow flow, Execution execution) implements HasUID, DispatchEvent {
    @Override
    public String uid() {
        return IdUtils.fromParts(flow.uidWithoutRevision(), execution.getId());
    }

    @Override
    public String key() {
        return uid();
    }
}
