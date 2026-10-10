package io.kestra.core.exceptions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.input.StringInput;

import static org.assertj.core.api.Assertions.assertThat;

class InputOutputValidationExceptionTest {

    @Test
    void shouldCreateSingleExceptionWithInputId() {
        StringInput input = StringInput.builder().id("myInput").build();
        InputOutputValidationException ex = InputOutputValidationException.of("Invalid format", input);

        assertThat(ex.getInputId()).isEqualTo("myInput");
        assertThat(ex.getMessage()).isEqualTo("Invalid value for input `myInput`. Cause: Invalid format");
        assertThat(ex.isRenderError()).isFalse();
    }

    @Test
    void shouldCreateRenderErrorException() {
        StringInput input = StringInput.builder().id("myInput").build();
        InputOutputValidationException ex = InputOutputValidationException.ofRenderError("Pebble evaluation failed", input);

        assertThat(ex.getInputId()).isEqualTo("myInput");
        assertThat(ex.isRenderError()).isTrue();
    }

    @Test
    void shouldMergeMultipleExceptionsAndAccumulateFieldErrors() {
        InputOutputValidationException ex1 = InputOutputValidationException.of("Pattern mismatch", StringInput.builder().id("field1").build());
        InputOutputValidationException ex2 = InputOutputValidationException.of("Too short", StringInput.builder().id("field1").build());
        InputOutputValidationException ex3 = InputOutputValidationException.of("Missing required input:field2", "field2");

        InputOutputValidationException composite = InputOutputValidationException.merge(List.of(ex1, ex2, ex3));

        assertThat(composite.getExceptions()).containsExactly(ex1, ex2, ex3);
        assertThat(composite.getFieldErrors()).hasSize(2);
        assertThat(composite.getFieldErrors().get("field1")).containsExactly(
            "Invalid value for input `field1`. Cause: Pattern mismatch",
            "Invalid value for input `field1`. Cause: Too short"
        );
        assertThat(composite.getFieldErrors().get("field2")).containsExactly(
            "Missing required input:field2"
        );
        assertThat(composite.getMessage()).contains("field1", "field2");
    }

    @Test
    void shouldFlattenNestedCompositeExceptionsWhenMerged() {
        InputOutputValidationException ex1 = InputOutputValidationException.of("Error 1", "field1");
        InputOutputValidationException ex2 = InputOutputValidationException.of("Error 2", "field2");
        InputOutputValidationException composite1 = InputOutputValidationException.merge(List.of(ex1, ex2));

        InputOutputValidationException ex3 = InputOutputValidationException.of("Error 3", "field3");
        InputOutputValidationException composite2 = InputOutputValidationException.merge(List.of(composite1, ex3));

        assertThat(composite2.getExceptions()).containsExactly(ex1, ex2, ex3);
        assertThat(composite2.getFieldErrors()).containsOnlyKeys("field1", "field2", "field3");
    }
}
