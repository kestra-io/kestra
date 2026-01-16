package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

    public enum Behavior {
        QUEUE, CANCEL, FAIL;
    }

    public static boolean possibleTransitions(State.Type type) {
        return type.equals(State.Type.CANCELLED) || type.equals(State.Type.FAILED);
    }
}
