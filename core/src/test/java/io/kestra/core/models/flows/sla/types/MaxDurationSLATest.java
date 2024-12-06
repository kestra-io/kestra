package io.kestra.core.models.flows.sla.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.Violation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;


class MaxDurationSLATest {

    @Test
    void shouldEvaluateToAViolation() throws InternalException, InterruptedException {
        MaxDurationSLA maxDurationSLA = MaxDurationSLA.builder()
            .duration(Duration.ofMillis(50))
            .build();
        Execution execution = Execution.builder()
            .state(new State().withState(State.Type.RUNNING))
            .build();
        Thread.sleep(100);

        Optional<Violation> evaluate = maxDurationSLA.evaluate(null, execution);
        assertTrue(evaluate.isPresent());
        assertThat(evaluate.get().reason(), containsString("execution duration of"));
        assertThat(evaluate.get().reason(), containsString("exceed the maximum duration of PT0.05S."));
    }

    @Test
    void shouldEvaluateToNoViolation() throws InternalException {
        MaxDurationSLA maxDurationSLA = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(10))
            .build();
        Execution execution = Execution.builder()
            .state(new State().withState(State.Type.RUNNING))
            .build();

        Optional<Violation> evaluate = maxDurationSLA.evaluate(null, execution);
        assertTrue(evaluate.isEmpty());
    }
}