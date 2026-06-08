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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class MultiselectInputTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldRenderInputGivenExpressionReturningStrings() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of("V1", "V2")));
        MultiselectInput input = MultiselectInput
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
        Assertions.assertEquals(((MultiselectInput) renderInput).getValues(), List.of(new ValueOption("V1", "V1"), new ValueOption("V2", "V2")));
    }

    @Test
    void shouldRenderInputGivenExpressionReturningIntegers() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of(1, 2)));
        MultiselectInput input = MultiselectInput
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
        Assertions.assertEquals(((MultiselectInput) renderInput).getValues(), List.of(new ValueOption("1", "1"), new ValueOption("2", "2")));
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
        MultiselectInput input = MultiselectInput
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
            ((MultiselectInput) renderInput).getValues()
        );
    }

    @Test
    void staticAutoselectFirst() throws IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();
        MultiselectInput input = MultiselectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("V1", "V1"), new ValueOption("V2", "V2")))
            .autoSelectFirst(true)
            .build();

        Assertions.assertEquals(List.of("V1"), runContext.render(input.getDefaults()).asList(String.class));
    }

    @Test
    void autoselectFirstUsesValueNotLabel() throws IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();
        MultiselectInput input = MultiselectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("Prod", "123"), new ValueOption("Staging", "456")))
            .autoSelectFirst(true)
            .build();

        Assertions.assertEquals(List.of("123"), runContext.render(input.getDefaults()).asList(String.class));
    }

    @Test
    void dynamicAutoselectFirst() throws IllegalVariableEvaluationException {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of("V1", "V2")));
        MultiselectInput input = MultiselectInput
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
        Assertions.assertEquals(List.of("V1"), runContext.render(((MultiselectInput) renderInput).getDefaults()).asList(String.class));
    }

    @Test
    void validateAcceptsValuesButNotLabels() {
        MultiselectInput input = MultiselectInput
            .builder()
            .id("id")
            .values(List.of(new ValueOption("Prod", "123"), new ValueOption("Staging", "456")))
            .build();

        // values pass
        input.validate(List.of("123", "456"));

        // labels fail
        assertThatThrownBy(() -> input.validate(List.of("Prod")))
            .hasMessageContaining("[123, 456]");
    }
}
