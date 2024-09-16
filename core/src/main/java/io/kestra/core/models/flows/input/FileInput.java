package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.validations.FileInputValidation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;

@SuperBuilder
@Getter
@NoArgsConstructor
@FileInputValidation
public class FileInput extends Input<URI> {

    private static final String DEFAULT_EXTENSION = ".upl";

    @Builder.Default
    public String extension = DEFAULT_EXTENSION;

    @Override
    public void validate(URI input) throws ConstraintViolationException {
        // no validation yet
    }

    public static String findFileInputExtension(@NotNull final List<Input<?>> inputs, @NotNull final String fileName) {
        String res = inputs.stream()
            .filter(in -> in instanceof FileInput)
            .filter(in -> in.getId().equals(fileName))
            .map(flowInput -> ((FileInput) flowInput).getExtension())
            .findFirst()
            .orElse(FileInput.DEFAULT_EXTENSION);
        return res.startsWith(".") ? res : "." + res;
    }
}
