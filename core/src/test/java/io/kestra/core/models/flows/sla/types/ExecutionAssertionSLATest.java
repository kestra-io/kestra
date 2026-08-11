package io.kestra.core.models.flows.sla.types;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.runners.RunContext;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class ExecutionAssertionSLATest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldEvaluateToAViolation() throws InternalException {
        ExecutionAssertionSLA sla = ExecutionAssertionSLA.builder()
            ._assert("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of(Map.of("condition", "false"));

        Optional<Violation> evaluate = sla.evaluate(runContext, null);
        assertTrue(evaluate.isPresent());
        assertThat(evaluate.get().reason()).isEqualTo("assertion is false: {{ condition == 'true'}}.");
    }

    @Test
    void shouldEvaluateToNoViolation() throws InternalException {
        ExecutionAssertionSLA sla = ExecutionAssertionSLA.builder()
            ._assert("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of(Map.of("condition", "true"));

        Optional<Violation> evaluate = sla.evaluate(runContext, null);
        assertTrue(evaluate.isEmpty());
    }

    @Test
    void shouldFailToEvaluate() throws InternalException {
        ExecutionAssertionSLA sla = ExecutionAssertionSLA.builder()
            ._assert("{{ condition == 'true'}}")
            .build();
        RunContext runContext = runContextFactory.of();

        assertThrows(InternalException.class, () -> sla.evaluate(runContext, null));
    }
}