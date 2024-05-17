package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;
@SuperBuilder
@Getter
@NoArgsConstructor
public class IntInput extends Input<Integer> {
    @Schema(title = "Minimal value.")
    Integer min;

    @Schema(title = "Maximal value.")
    Integer max;

    @Override
    public void validate(Integer input) throws ConstraintViolationException {
        if (min != null && input.compareTo(min) < 0) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be more than '" + min + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    IntInput.class,
                    getId(),
                    input
                )));
        }

        if (max != null && input.compareTo(max) > 0) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be less than '" + max + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    IntInput.class,
                    getId(),
                    input
                )));
        }
    }
}
