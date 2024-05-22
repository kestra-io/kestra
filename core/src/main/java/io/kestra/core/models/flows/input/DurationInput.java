package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

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
            throw ManualConstraintViolation.toConstraintViolationException(
                "It must be more than `" + min + "`",
                this,
                DurationInput.class,
                getId(),
                input
            );
        }

        if (max != null && input.compareTo(max) > 0) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "It must be less than `" + max + "`",
                this,
                DurationInput.class,
                getId(),
                input
            );
        }
    }
}
