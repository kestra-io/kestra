package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Set;
import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DateInput extends Input<LocalDate> {
    @Schema(title = "Minimal value.")
    LocalDate after;

    @Schema(title = "Maximal value.")
    LocalDate before;

    @Override
    public void validate(LocalDate input) throws ConstraintViolationException {
        if (after != null && input.isBefore(after)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be after '" + after + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    DateInput.class,
                    getId(),
                    input
                )));
        }

        if (before != null && input.isAfter(before)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be before '" + before + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    DateInput.class,
                    getId(),
                    input
                )));
        }
    }
}
