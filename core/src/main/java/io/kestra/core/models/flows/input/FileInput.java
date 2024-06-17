package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.validations.FileInputValidation;
import jakarta.validation.ConstraintViolationException;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@Getter
@NoArgsConstructor
@FileInputValidation
public class FileInput extends Input<URI> {
    @Builder.Default
    public String extension = ".upl";

    @Override
    public void validate(URI input) throws ConstraintViolationException {
        // no validation yet
    }
}
