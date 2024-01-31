package io.kestra.core.validations;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class InputTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void inputValidation() {
        final StringInput validInput = StringInput.builder()
            .id("test")
            .type(Input.Type.STRING)
            .validator("[A-Z]+")
            .build();

        assertThat(modelValidator.isValid(validInput).isEmpty(), is(true));
    }
}
