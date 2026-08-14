package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;

import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * A flow input containing Amazon Ion text or an already structured value.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
public class IonInput extends Input<Object> {
    @Override
    public void validate(Object input) throws ConstraintViolationException {
        // no validation yet
    }
}
