package io.kestra.core.models.flows.quota;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

/**
 * Represents a quota for a flow.
 * The duration is the identifier.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
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
