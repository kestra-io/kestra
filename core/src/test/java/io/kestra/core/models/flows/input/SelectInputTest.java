package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class SelectInputTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldRenderInputGivenExpressionReturningStrings() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of("V1", "V2")));
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .expression("{{ values }}\n")
            .build();
        // When
        Input<?> renderInput = RenderableInput.mayRenderInput(input, s ->
        {
            try {
                return runContext.renderTyped(s);
            } catch (IllegalVariableEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
        // Then
        Assertions.assertEquals(((SelectInput) renderInput).getValues(), List.of(new ValueOption("V1", "V1"), new ValueOption("V2", "V2")));
    }

    @Test
    void shouldRenderInputGivenExpressionReturningIntegers() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of(1, 2)));
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .expression("{{ values }}")
            .build();
        // When
        Input<?> renderInput = RenderableInput.mayRenderInput(input, s ->
        {
            try {
                return runContext.renderTyped(s);
            } catch (IllegalVariableEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
        // Then
        Assertions.assertEquals(((SelectInput) renderInput).getValues(), List.of(new ValueOption("1", "1"), new ValueOption("2", "2")));
    }

    @Test
    void shouldRenderInputGivenExpressionReturningLabelValueObjects() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of(
            "options",
            List.of(
                Map.of("label", "Prod", "value", "123"),
                Map.of("label", "Staging", "value", "456")
            )
        ));
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .expression("{{ options }}")
            .build();
        // When
        Input<?> renderInput = RenderableInput.mayRenderInput(input, s ->
        {
            try {
                return runContext.renderTyped(s);
            } catch (IllegalVariableEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
        // Then
        Assertions.assertEquals(
            List.of(new ValueOption("Prod", "123"), new ValueOption("Staging", "456")),
            ((SelectInput) renderInput).getValues()
        );
    }

    @Test
    void staticAutoselectFirst() throws IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("V1", "V1"), new ValueOption("V2", "V2")))
            .autoSelectFirst(true)
            .build();

        Assertions.assertEquals("V1", runContext.render(input.getDefaults()).as(String.class).orElseThrow());
    }

    @Test
    void autoselectFirstUsesValueNotLabel() throws IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("Prod", "123"), new ValueOption("Staging", "456")))
            .autoSelectFirst(true)
            .build();

        Assertions.assertEquals("123", runContext.render(input.getDefaults()).as(String.class).orElseThrow());
    }

    @Test
    void dynamicAutoselectFirst() throws IllegalVariableEvaluationException {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of("V1", "V2")));
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .expression("{{ values }}")
            .autoSelectFirst(true)
            .build();

        Assertions.assertNull(input.getDefaults());

        // When
        Input<?> renderInput = RenderableInput.mayRenderInput(input, s ->
        {
            try {
                return runContext.renderTyped(s);
            } catch (IllegalVariableEvaluationException e) {
                throw new RuntimeException(e);
            }
        });

        // Then
        Assertions.assertEquals("V1", runContext.render(((SelectInput) renderInput).getDefaults()).as(String.class).orElseThrow());
    }

    @Test
    void validateAcceptsValueButNotLabel() {
        SelectInput input = SelectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("Prod", "123"), new ValueOption("Staging", "456")))
            .required(true)
            .build();

        // value passes
        input.validate("123");

        // label does not
        assertThatThrownBy(() -> input.validate("Prod"))
            .hasMessageContaining("[123, 456]");
    }

    @Test
    void valueOptionDeserializesFromStringOrObject() {
        ValueOption fromString = ValueOption.from("V1");
        assertThat(fromString.label()).isEqualTo("V1");
        assertThat(fromString.value()).isEqualTo("V1");

        ValueOption fromMap = ValueOption.from(Map.of("label", "Prod", "value", "123"));
        assertThat(fromMap.label()).isEqualTo("Prod");
        assertThat(fromMap.value()).isEqualTo("123");

        // value-only map: label defaults to value
        ValueOption valueOnly = ValueOption.from(Map.of("value", "789"));
        assertThat(valueOnly.label()).isEqualTo("789");
        assertThat(valueOnly.value()).isEqualTo("789");
    }
}
