package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;
import java.util.Set;
import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class TimeInput extends Input<LocalTime> {
    @Schema(title = "Minimal value.")
    LocalTime after;

    @Schema(title = "Maximal value.")
    LocalTime before;

    @Override
    public void validate(LocalTime input) throws ConstraintViolationException {
        if (after != null && input.isBefore(after)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be after '" + after + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    TimeInput.class,
                    getName(),
                    input
                )));
        }

        if (before != null && input.isAfter(before)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be before '" + before + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    TimeInput.class,
                    getName(),
                    input
                )));
        }
    }
}
