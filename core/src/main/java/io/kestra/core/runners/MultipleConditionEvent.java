package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.IdUtils;

public record MultipleConditionEvent(Flow flow, Execution execution) implements HasUID {
    @Override
    public String uid() {
        return IdUtils.fromParts(flow.uidWithoutRevision(), execution.getId());
    }
}
