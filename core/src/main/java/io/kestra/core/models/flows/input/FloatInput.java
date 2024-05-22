package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class FloatInput extends Input<Float> {
    @Schema(title = "Minimal value.")
    Float min;

    @Schema(title = "Maximal value.")
    Float max;

    @Override
    public void validate(Float input) throws ConstraintViolationException {
        if (min != null && input.compareTo(min) < 0) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be more than `" + min + "`",
                this,
                FloatInput.class,
                getId(),
                input
            );
        }

        if (max != null && input.compareTo(max) > 0) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must be less than `" + max + "`",
                this,
                FloatInput.class,
                getId(),
                input
            );
        }
    }
}
