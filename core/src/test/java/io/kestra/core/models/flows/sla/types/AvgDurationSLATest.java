package io.kestra.core.models.flows.sla.types;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;

import static org.assertj.core.api.Assertions.assertThat;

class AvgDurationSLATest {

    @Test
    void shouldEvaluateToAViolationWhenExecutionExceedsComputedDuration() throws InternalException, InterruptedException {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var execution = Execution.builder().state(new State().withState(State.Type.RUNNING)).build();
        Thread.sleep(100);

        // When
        Optional<Violation> violation = sla.evaluate(null, execution, Map.of(SLA.SLA_DURATION_ATTRIBUTE, Duration.ofMillis(50)));

        // Then
        assertThat(violation).isPresent();
        assertThat(violation.get().reason()).contains("exceed the average duration of PT0.05S.");
    }

    @Test
    void shouldEvaluateToNoViolationWhenExecutionIsBelowComputedDuration() throws InternalException {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var execution = Execution.builder().state(new State().withState(State.Type.RUNNING)).build();

        // When
        Optional<Violation> violation = sla.evaluate(null, execution, Map.of(SLA.SLA_DURATION_ATTRIBUTE, Duration.ofSeconds(10)));

        // Then
        assertThat(violation).isEmpty();
    }
}
