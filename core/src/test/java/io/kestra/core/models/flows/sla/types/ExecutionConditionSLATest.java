package io.kestra.core.models.flows.sla.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class ExecutionConditionSLATest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldEvaluateToAViolation() throws InternalException {
        ExecutionConditionSLA sla = ExecutionConditionSLA.builder()
            .condition("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of(Map.of("condition", "true"));

        Optional<Violation> evaluate = sla.evaluate(runContext, null);
        assertTrue(evaluate.isPresent());
        assertThat(evaluate.get().reason(), is("condition met: {{ condition == 'true'}}."));
    }

    @Test
    void shouldEvaluateToNoViolation() throws InternalException {
        ExecutionConditionSLA sla = ExecutionConditionSLA.builder()
            .condition("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of(Map.of("condition", "false"));

        Optional<Violation> evaluate = sla.evaluate(runContext, null);
        assertTrue(evaluate.isEmpty());
    }

    @Test
    void shouldFailToEvaluate() throws InternalException {
        ExecutionConditionSLA sla = ExecutionConditionSLA.builder()
            .condition("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of();

        assertThrows(InternalException.class, () -> sla.evaluate(runContext, null));
    }
}