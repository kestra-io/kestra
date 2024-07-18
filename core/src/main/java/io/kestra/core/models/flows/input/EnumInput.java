package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@SuperBuilder
@Getter
@NoArgsConstructor
public class EnumInput extends Input<String> {
    @Schema(
        title = "List of values."
    )
    @Regex
    @NotNull
    List<String> values;

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (!values.contains(input) & this.getRequired()) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must match the values '" + values + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    EnumInput.class,
                    getId(),
                    input
                )));
        }
    }
}
