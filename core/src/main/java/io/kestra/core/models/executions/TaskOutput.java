package io.kestra.core.models.executions;

import io.kestra.core.models.HasUID;

public record TaskOutput(String taskRunId, String tenantId, String executionId, byte[] value, String uri) implements HasUID {
    @Override
    public String uid() {
        return taskRunId;
    }
}
