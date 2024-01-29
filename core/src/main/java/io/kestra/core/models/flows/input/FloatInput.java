package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import jakarta.validation.ConstraintViolationException;

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
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be more than '" + min + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    FloatInput.class,
                    getName(),
                    input
                )));
        }

        if (max != null && input.compareTo(max) > 0) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be less than '" + max + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    FloatInput.class,
                    getName(),
                    input
                )));
        }
    }
}
