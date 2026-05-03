package io.kestra.core.async;

import jakarta.annotation.Nullable;

/**
 * Marker interface for domain events that can be associated with an async operation.
 * <p>
 * Events implementing this interface carry a nullable {@code operationId} that groups them under a
 * user-initiated async API call. Domain consumers that process such events must publish an
 * {@link AsyncOperationProcessedEvent} after processing.
 */
public interface AsyncOperation {
    /**
     * @return the operation identifier, or {@code null} when the event is not part of an async operation.
     */
    @Nullable
    default String operationId() {
        return null;
    }
}
