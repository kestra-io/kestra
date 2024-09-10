package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Getter
@NoArgsConstructor
public class SelectInput extends Input<String> {
    @Schema(
        title = "List of values."
    )
    @NotNull
    List<@Regex String> values;

    @Schema(
        title = "If the user can provide a custom value."
    )
    @NotNull
    @Builder.Default
    Boolean allowInput = false;


    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (!values.contains(input) & this.getRequired()) {
            if (this.getAllowInput()) {
                return;
            }
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must match the values `" + values + "`",
                this,
                SelectInput.class,
                getId(),
                input
            );
        }
    }
}
