package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
public class Concurrency {
    @Min(1)
    @NotNull
    private Integer limit;

    @NotNull
    @Builder.Default
    private Behavior behavior = Behavior.QUEUE;

    @Valid
    @Schema(
        title = "Maximum duration an execution can hold a concurrency slot.",
        description = "If an execution holds a slot longer than this duration (e.g., due to executor crash, " +
            "pod eviction, or network partition), the slot will be automatically released. " +
            "This prevents orphaned slots from blocking subsequent executions indefinitely. " +
            "Format is ISO 8601 duration (e.g., PT1H for 1 hour, PT30M for 30 minutes)."
    )
    private Duration duration;

    public enum Behavior {
        QUEUE, CANCEL, FAIL;
    }

    public static boolean possibleTransitions(State.Type type) {
        return type.equals(State.Type.CANCELLED) || type.equals(State.Type.FAILED);
    }
}
