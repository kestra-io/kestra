package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class URIInput extends Input<String> {
    @Override
    public void validate(String input) throws ConstraintViolationException {
        // no validation yet
    }
}
