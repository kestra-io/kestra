package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

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
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be after `" + after + "`",
                this,
                DateInput.class,
                getId(),
                input
            );
        }

        if (before != null && input.isAfter(before)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be before `" + before + "`",
                this,
                DateInput.class,
                getId(),
                input
            );
        }
    }
}
