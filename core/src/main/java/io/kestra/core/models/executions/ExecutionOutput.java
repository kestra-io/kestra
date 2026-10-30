package io.kestra.core.models.executions;

import io.kestra.core.models.HasUID;

public record ExecutionOutput(String executionId, String tenantId, byte[] value, String uri) implements HasUID {
    @Override
    public String uid() {
        return executionId;
    }
}
