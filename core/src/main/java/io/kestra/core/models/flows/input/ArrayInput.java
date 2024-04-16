package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Set;

@SuperBuilder
@Getter
@NoArgsConstructor
public class ArrayInput extends Input<List<?>> {
    @Schema(
        title = "Type of the array items.",
        description = "Cannot be of type `ARRAY`."
    )
    @NotNull
    private Type itemType;

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        if (Type.ARRAY.equals(itemType)) {
            throw new ConstraintViolationException("Invalid input definition: `itemType` cannot be `ARRAY`",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    ArrayInput.class,
                    getId(),
                    input
                )));
        }
    }
}
