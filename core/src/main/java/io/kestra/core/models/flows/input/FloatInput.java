package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class FloatInput extends Input<Float> {
    @Override
    public void validate(Float input) throws ConstraintViolationException {
        // no validation yet
    }
}
