package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class CorrelationIdTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;
    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows(value = {"flows/valids/subflow-parent.yaml",
        "flows/valids/subflow-child.yaml",
        "flows/valids/subflow-grand-child.yaml"}, tenantId = "shouldhavecorrelationid")
    void shouldHaveCorrelationId() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("shouldhavecorrelationid", "io.kestra.tests", "subflow-parent");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution child = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().isTerminated(), "shouldhavecorrelationid",
            "io.kestra.tests", "subflow-child");

        assertThat(child.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Optional<Label> correlationId = child.getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());

        Execution grandChild = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().isTerminated(), "shouldhavecorrelationid",
            "io.kestra.tests", "subflow-grand-child");
        assertThat(grandChild.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        correlationId = grandChild.getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());
    }
}
