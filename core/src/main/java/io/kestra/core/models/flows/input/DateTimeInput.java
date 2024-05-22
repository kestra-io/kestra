package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DateTimeInput extends Input<Instant> {
    @Schema(title = "Minimal value.")
    Instant after;

    @Schema(title = "Maximal value.")
    Instant before;

    @Override
    public void validate(Instant input) throws ConstraintViolationException {
        if (after != null && input.isBefore(after)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be after `" + after + "`",
                this,
                DateTimeInput.class,
                getId(),
                input
            );
        }

        if (before != null && input.isAfter(before)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be before `" + before + "`",
                this,
                DateTimeInput.class,
                getId(),
                input
            );
        }
    }
}
