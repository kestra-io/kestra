package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.Set;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class FileInput extends Input<URI> {
    @Schema(title = "The file extension.")
    String extension;

    @Override
    public void validate(URI input) throws ConstraintViolationException {
        if (extension != null && !input.getPath().endsWith(extension)) {
            throw new ConstraintViolationException("Invalid input '" + input + "', it must be a file with the extension '" + extension + "'",
                Set.of(ManualConstraintViolation.of(
                    "Invalid input",
                    this,
                    FileInput.class,
                    getName(),
                    input
                )));
        }
    }
}
