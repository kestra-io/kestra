package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.With;

@Builder(toBuilder = true)
@Setter
@Getter
public class ExecutionMetadata {
    @Builder.Default
    @With
    Integer attemptNumber = 1;

    @NotNull
    Instant originalCreatedDate;

    /**
     * The uids of the concurrency scopes this execution claimed a slot in when it was admitted.
     * The release decrements exactly these scopes, so removing or changing a namespace/tenant
     * limit while the execution runs cannot leak the counter of a scope it was admitted under.
     * Null when the execution never claimed a slot (or predates the scoped limits).
     */
    @With
    List<String> concurrencyScopes;

    public ExecutionMetadata nextAttempt() {
        return this.toBuilder()
            .attemptNumber(this.attemptNumber + 1)
            .build();
    }
}
