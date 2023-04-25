package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.regex.Pattern;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class StringInput extends Input<String> {
    @Schema(
        title = "Regular expression validating the value."
    )
    @Regex
    String validator;

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (validator != null && ! Pattern.matches(validator, input)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must match the pattern '" + validator + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    StringInput.class,
                    getName(),
                    input
                )));
        }
    }
}
