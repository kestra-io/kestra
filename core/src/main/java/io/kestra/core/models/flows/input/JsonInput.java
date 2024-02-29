package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class JsonInput extends Input<Object> {
    @Override
    public void validate(Object input) throws ConstraintViolationException {
        // no validation yet
    }
}
