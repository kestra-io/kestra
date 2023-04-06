package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class IntInput extends Input<Integer> {
    @Override
    public void validate(Integer input) throws ConstraintViolationException {
        // no validation yet
    }
}
