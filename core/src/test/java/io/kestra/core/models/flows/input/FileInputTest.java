package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

class FileInputTest {

    @Test
    void shouldGetExtensionWhenFindingFileExtensionForExistingFile() {
        List<Input<?>> inputs = List.of(
            FileInput.builder().id("test-file1").extension(".zip").build(),
            FileInput.builder().id("test-file2").extension(".gz").build()
        );

        String result = FileInput.findFileInputExtension(inputs, "test-file1");
        Assertions.assertEquals(".zip", result);
    }

    @Test
    void shouldReturnDefaultExtensionWhenFindingExtensionForUnknownFile() {
        List<Input<?>> inputs = List.of(
            FileInput.builder().id("test-file1").extension(".zip").build(),
            FileInput.builder().id("test-file2").extension(".gz").build()
        );

        String result = FileInput.findFileInputExtension(inputs, "???");
        Assertions.assertEquals(".upl", result);
    }

    @Test
    void validateValidFileTypes() {
        final FileInput csvInput = FileInput.builder()
            .id("csvFile")
            .allowedFileExtensions(List.of(".csv"))
            .build();

        // Test valid CSV file
        assertDoesNotThrow(() -> csvInput.validate(URI.create("file:///path/to/file.csv")));
        assertDoesNotThrow(() -> csvInput.validate(URI.create("nsfile:///path/to/file.CSV"))); // Test case-insensitive

        // Test multiple extensions
        final FileInput docInput = FileInput.builder()
            .id("docFile")
            .allowedFileExtensions(List.of(".doc", ".docx", ".pdf"))
            .build();

        assertDoesNotThrow(() -> docInput.validate(URI.create("file:///path/to/file.doc")));
        assertDoesNotThrow(() -> docInput.validate(URI.create("file:///path/to/file.docx")));
        assertDoesNotThrow(() -> docInput.validate(URI.create("file:///path/to/file.pdf")));
    }

    @Test
    void validateInvalidFileTypes() {
        final FileInput csvInput = FileInput.builder()
            .id("csvFile")
            .allowedFileExtensions(List.of(".csv"))
            .build();

        // Test invalid extension
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> csvInput.validate(URI.create("file:///path/to/file.txt"))
        );
        assertThat(exception.getMessage(), containsString("Accepted extensions: .csv"));

        // Test multiple allowed types
        final FileInput imageInput = FileInput.builder()
            .id("imageFile")
            .allowedFileExtensions(List.of(".jpg", ".png"))
            .build();

        exception = assertThrows(
            ConstraintViolationException.class,
            () -> imageInput.validate(URI.create("file:///path/to/file.gif"))
        );
        assertThat(exception.getMessage(), containsString("Accepted extensions: .jpg, .png"));
    }

    @Test
    void validateMimeTypes() {
        final FileInput textInput = FileInput.builder()
            .id("textFile")
            .allowedFileExtensions(List.of(".csv", ".json"))
            .build();

        // Test valid file types
        assertDoesNotThrow(() -> textInput.validate(URI.create("file:///path/to/file.csv")));
        assertDoesNotThrow(() -> textInput.validate(URI.create("file:///path/to/file.json")));

        // Test invalid file type
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> textInput.validate(URI.create("file:///path/to/file.xml"))
        );
        assertThat(exception.getMessage(), containsString("Accepted extensions: .csv, .json"));
    }

    @Test
    void validateNullValues() {
        final FileInput csvInput = FileInput.builder()
            .id("csvFile")
            .allowedFileExtensions(List.of(".csv"))
            .build();

        // Null input should be allowed (for optional inputs)
        assertDoesNotThrow(() -> csvInput.validate(null));

        // Null extensions should not enforce any validation
        final FileInput anyInput = FileInput.builder()
            .id("anyFile")
            .allowedFileExtensions(null)
            .build();
        assertDoesNotThrow(() -> anyInput.validate(URI.create("file:///path/to/any.file")));
    }

    @Test
    void validateEmptyAccept() {
        final FileInput anyInput = FileInput.builder()
            .id("anyFile")
            .allowedFileExtensions(List.of())
            .build();

        // Empty extensions list should not enforce any validation
        assertDoesNotThrow(() -> anyInput.validate(URI.create("file:///path/to/any.file")));
    }
}
