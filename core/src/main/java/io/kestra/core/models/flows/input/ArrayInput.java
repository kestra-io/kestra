package io.kestra.core.models.flows.input;

import java.util.List;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.validations.ArrayInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@ArrayInputValidation
public class ArrayInput extends Input<List<?>> implements ItemTypeInterface {
    @Schema(
        title = "Type of the array items.",
        description = "Cannot be of type `ARRAY`."
    )
    @NotNull
    private Type itemType;

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        // no validation yet
    }
}
