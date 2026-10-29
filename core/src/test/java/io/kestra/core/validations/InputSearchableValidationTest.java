package io.kestra.core.validations;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.JsonInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class InputSearchableValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptASearchableInputOfASupportedType() {
        assertThat(modelValidator.isValid(StringInput.builder().id("input").type(Type.STRING).searchable(true).build())).isEmpty();
    }

    @Test
    void shouldRejectASearchableInputOfAnUnsupportedType() {
        Optional<ConstraintViolationException> violation = modelValidator.isValid(JsonInput.builder().id("input").type(Type.JSON).searchable(true).build());

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("The input 'input' cannot be searchable").contains("JSON");
    }

    @Test
    void shouldAcceptAnyTypeWhenTheInputIsNotSearchable() {
        assertThat(modelValidator.isValid(JsonInput.builder().id("input").type(Type.JSON).build())).isEmpty();
        assertThat(modelValidator.isValid(JsonInput.builder().id("input").type(Type.JSON).searchable(false).build())).isEmpty();
    }

    @Test
    void shouldRejectAFlowWithASearchableInputOfAnUnsupportedType() {
        Flow flow = Flow.builder()
            .id("flow")
            .namespace("io.kestra.tests")
            .inputs(List.<Input<?>>of(JsonInput.builder().id("input").type(Type.JSON).searchable(true).build()))
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();

        assertThat(modelValidator.isValid(flow)).get().satisfies(violation -> assertThat(violation.getMessage()).contains("The input 'input' cannot be searchable"));
    }
}
