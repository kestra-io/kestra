package io.kestra.core.models.executions;

import java.time.Instant;

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

    Instant originalCreatedDate;

    public ExecutionMetadata nextAttempt() {
        return this.toBuilder()
            .attemptNumber(this.attemptNumber + 1)
            .build();
    }
}
