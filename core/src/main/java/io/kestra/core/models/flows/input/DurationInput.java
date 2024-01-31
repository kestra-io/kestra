package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Set;
import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DurationInput extends Input<Duration> {
    @Schema(title = "Minimal value.")
    Duration min;

    @Schema(title = "Maximal value.")
    Duration max;

    @Override
    public void validate(Duration input) throws ConstraintViolationException {
        if (min != null && input.compareTo(min) < 0) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be more than '" + min + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    DurationInput.class,
                    getId(),
                    input
                )));
        }

        if (max != null && input.compareTo(max) > 0) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be less than '" + max + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    DurationInput.class,
                    getId(),
                    input
                )));
        }
    }
}
