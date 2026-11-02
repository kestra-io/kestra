package io.kestra.core.checkpoint;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;

public record Checkpoint(String tenantId, String executionId, String taskRunId, String key, String value) implements HasUID {
    public Checkpoint {
        if (value == null) {
            throw new IllegalArgumentException("The value of the checkpoint '%s' cannot be null.".formatted(key));
        }
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(tenantId, executionId, taskRunId, key);
    }
}
