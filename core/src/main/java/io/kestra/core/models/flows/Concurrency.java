package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
public class Concurrency {
    @Positive
    @NotNull
    private Integer limit;

    @NotNull
    @Builder.Default
    private Behavior behavior = Behavior.QUEUE;

    public enum Behavior {
        QUEUE, CANCEL, FAIL;
    }
}
