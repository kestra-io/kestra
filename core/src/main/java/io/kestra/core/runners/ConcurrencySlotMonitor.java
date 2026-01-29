package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tracks concurrency slot acquisition with a deadline for automatic release.
 * When an execution acquires a concurrency slot and the flow has a duration configured,
 * a monitor is created with a deadline. If the execution holds the slot past the deadline
 * (e.g., due to executor crash, pod eviction, or network partition), the slot will be
 * automatically released.
 */
@Builder
@Getter
public class ConcurrencySlotMonitor implements HasUID {
    private String tenantId;

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private String executionId;

    @NotNull
    private Instant deadline;

    @Override
    public String uid() {
        return IdUtils.fromParts(tenantId, namespace, flowId, executionId);
    }
}
