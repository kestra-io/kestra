package io.kestra.core.models.notifications;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One resource targeted by an {@link io.kestra.core.server.AsyncOperationType async operation}
 * (an execution id or a trigger uid — see {@link io.kestra.core.server.AsyncOperationType#resourceType()}),
 * and its processing outcome.
 * <p>
 * The physical primary key is {@link #uid()}, a hash of the {@code (operationId, resourceId)}
 * pair.
 */
@Builder(toBuilder = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItem implements HasUID {
    @NotNull
    private String operationId;

    @Nullable
    private String tenantId;

    @NotNull
    private String resourceId;

    @NotNull
    @Builder.Default
    private NotificationItemOutcome outcome = NotificationItemOutcome.PENDING;

    @NotNull
    private Instant updated;

    @Override
    public String uid() {
        return IdUtils.from(operationId + resourceId);
    }
}
