package io.kestra.core.models.flows.input;

import java.util.List;

import io.kestra.core.models.flows.Input;

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
public class FormInput extends Input<Void> {
    @Schema(
        title = "The inputs grouped under this form.",
        description = "A FORM groups related inputs under a shared display name. Each child input is resolved " +
            "under the form's id as a nested path — e.g. a `region` input inside an `environment` form is " +
            "referenced as `{{ inputs.environment.region }}`. To depend on a sibling, use the full dotted path " +
            "in `dependsOn` (e.g. `dependsOn: [environment.data_center]`). Forms cannot be nested."
    )
    @NotNull
    @Valid
    private List<Input<?>> inputs;

    @Override
    public void validate(Void input) throws ConstraintViolationException {
        // no-op: a FORM is a structural wrapper expanded into its children before resolution
    }
}
