package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;

import jakarta.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class FileInput extends Input<URI> {
    @Override
    public void validate(URI input) throws ConstraintViolationException {
        // no validation yet
    }
}
