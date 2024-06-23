package io.kestra.core.validations;

import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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

        assertThat(modelValidator.isValid(validInput).isEmpty(), is(true));
    }

    @Test
    void inputNameDeprecation() {
        String id = "test";
        StringInput validInput = StringInput.builder()
            .id(id)
            .type(Type.STRING)
            .build();

        assertThat(validInput.getId(), is(id));
        assertThat(validInput.getId(), nullValue());

        String newName = "newName";
        validInput = StringInput.builder()
            .type(Type.STRING)
            .build();

        validInput.setName(newName);

        assertThat(validInput.getId(), is(newName));
        assertThat(validInput.getId(), is(newName));
    }
}
