package io.kestra.core.models.flows.input;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@KestraTest
class MultiselectInputTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldRenderInputGivenExpression() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("values", List.of("V1", "V2")));
        MultiselectInput input = MultiselectInput
            .builder()
            .id("id")
            .expression("{{ values }}")
            .build();
        // When
        Input<?> renderInput = RenderableInput.mayRenderInput(input, s -> {
            try {
                return runContext.renderTyped(s);
            } catch (IllegalVariableEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
        // Then
        Assertions.assertEquals(((MultiselectInput)renderInput).getValues(), List.of("V1", "V2"));
    }
}