package io.kestra.core.models.flows.input;

import java.util.Set;
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

    @Deprecated(since = "0.24", forRemoval = true)
    public String extension;
    
    /**
     * List of allowed file extensions (e.g., [".csv", ".txt", ".pdf"]).
     * Each extension must start with a dot.
     */
    private List<String> allowedFileExtensions;
    
    /**
     * Gets the file extension from the URI's path
     */
    private String getFileExtension(URI uri) {
        String path = uri.getPath();
        int lastDotIndex = path.lastIndexOf(".");
        return lastDotIndex >= 0 ? path.substring(lastDotIndex).toLowerCase() : "";
    }

    @Override
    public void validate(URI input) throws ConstraintViolationException {
        if (input == null || allowedFileExtensions == null || allowedFileExtensions.isEmpty()) {
            return;
        }

        String extension = getFileExtension(input);
        if (!allowedFileExtensions.contains(extension.toLowerCase())) {
            throw new ConstraintViolationException(
                "File type not allowed. Accepted extensions: " + String.join(", ", allowedFileExtensions),
                Set.of()
            );
        }
    }

    public static String findFileInputExtension(@NotNull final List<Input<?>> inputs, @NotNull final String fileName) {
        String res = inputs.stream()
            .filter(in -> in instanceof FileInput)
            .filter(in -> in.getId().equals(fileName))
            .filter(flowInput -> ((FileInput) flowInput).getExtension() != null)
            .map(flowInput -> ((FileInput) flowInput).getExtension())
            .findFirst()
            .orElse(FileInput.DEFAULT_EXTENSION);
        return res.startsWith(".") ? res : "." + res;
    }
}
