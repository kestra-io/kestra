package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BooleanInput extends Input<Boolean> {
    @Override
    public void validate(Boolean input) throws ConstraintViolationException {
        // no validation yet
    }
}
