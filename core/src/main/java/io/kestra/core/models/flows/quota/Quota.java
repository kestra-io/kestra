package io.kestra.core.models.flows.quota;

import java.time.Duration;

import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a quota for a flow.
 * The duration is the identifier.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class Quota {
    @NotNull
    @DurationMin(minutes = 1)
    private Duration duration;

    @NotNull
    @Positive
    private Long limit;

    @NotNull
    private Behavior behavior;

    public enum Behavior {
        FAIL,
        CANCEL;
    }
}
