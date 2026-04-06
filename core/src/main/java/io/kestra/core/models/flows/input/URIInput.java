package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;

import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class URIInput extends Input<String> {
    @Override
    public void validate(String input) throws ConstraintViolationException {
        // no validation yet
    }
}
