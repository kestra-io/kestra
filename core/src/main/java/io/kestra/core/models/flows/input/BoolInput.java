package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BoolInput extends Input<Boolean> {
    @Override
    public void validate(Boolean input) throws ConstraintViolationException {
        // no validation yet
    }
}
