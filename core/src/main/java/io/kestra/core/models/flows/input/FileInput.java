package io.kestra.core.models.flows.input;

import java.net.URI;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.flows.Input;
import io.kestra.core.validations.FileInputValidation;

import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@FileInputValidation
public class FileInput extends Input<URI> {

    public static final String DEFAULT_EXTENSION = ".upl";

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
}
