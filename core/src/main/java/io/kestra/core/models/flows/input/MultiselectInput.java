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

@SuperBuilder
@Getter
@NoArgsConstructor
public class MultiselectInput extends Input<List<String>> {
    @Schema(
        title = "List of string values."
    )
    @NotNull
    List<@Regex String> options;

    @Override
    public void validate(List<String> inputs) throws ConstraintViolationException {
        for(String input : inputs){
            if (!options.contains(input)) {
                throw ManualConstraintViolation.toConstraintViolationException(
                    "it must match the values `" + options + "`",
                    this,
                    MultiselectInput.class,
                    getId(),
                    input
                );
            }
        }
    }
}
