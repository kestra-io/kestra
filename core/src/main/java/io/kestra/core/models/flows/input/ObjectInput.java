package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.flows.Input;
import io.kestra.core.validations.ObjectInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@ObjectInputValidation
public class ObjectInput extends Input<Map<String, Object>> {
    @Schema(
        title = "The typed properties of the object.",
        description = "Each property is a regular input declaration (e.g. `INT` with `min`/`max`, `STRING` with a `validator`) " +
            "resolved and validated against the corresponding key of the submitted object. The value is referenced as " +
            "`{{ inputs.myObject.myProperty }}`. Properties cannot be of type `OBJECT`, `TABLE`, `ARRAY`, `FORM`, `FILE`, " +
            "`SECRET`, `JSON`, `ION` or `YAML`, and cannot declare `defaults`, `prefill`, `dependsOn` or an `expression`."
    )
    @NotNull
    @Valid
    private List<Input<?>> properties;

    @Override
    public void validate(Map<String, Object> input) throws ConstraintViolationException {
        // no-op: each property is validated by its own input while the object is parsed
    }
}
