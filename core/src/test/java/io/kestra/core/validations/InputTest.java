package io.kestra.core.validations;

import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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

    @SuppressWarnings("deprecation")
    @Test
    void inputNameDeprecation() {
        String id = "test";
        StringInput validInput = StringInput.builder()
            .id(id)
            .type(Type.STRING)
            .build();

        assertThat(validInput.getId()).isEqualTo(id);
        assertThat(validInput.getName()).isNull();

        String newName = "newName";
        validInput = StringInput.builder()
            .type(Type.STRING)
            .build();

        validInput.setName(newName);

        assertThat(validInput.getName()).isEqualTo(newName);
        assertThat(validInput.getId()).isEqualTo(newName);
    }
}
