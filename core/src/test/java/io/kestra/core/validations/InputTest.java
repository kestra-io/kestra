package io.kestra.core.validations;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class InputTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void inputValidation() {
        final StringInput validInput = StringInput.builder()
            .id("test")
            .type(Type.STRING)
            .validator("[A-Z]+")
            .build();

        assertThat(modelValidator.isValid(validInput).isEmpty()).isTrue();
    }

    @Test
    void shouldFailFileInputWithDefault() {
        var fileInput = FileInput.builder()
            .id("test")
            .type(Type.FILE)
            .defaults(Property.ofValue(URI.create("http://some.uri")))
            .build();

        assertThat(modelValidator.isValid(fileInput)).isPresent();
    }

    @Test
    void shouldValidateFileInputWithFileDefault() {
        var fileInput = FileInput.builder()
            .id("test")
            .type(Type.FILE)
            .defaults(Property.ofValue(URI.create("file:///tmp.file.txt")))
            .build();

        assertThat(modelValidator.isValid(fileInput)).isEmpty();
    }
}
