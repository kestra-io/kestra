package io.kestra.core.models.flows;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@SuperBuilder
@Getter
@NoArgsConstructor
public class ConcurrencyLimit {
    @Positive
    @NotNull
    private Integer maxConcurrency;

    @NotNull
    @Builder.Default
    private Behavior behavior = Behavior.QUEUE;

    public enum Behavior {
        QUEUE, CANCEL, FAIL;
    }
}
