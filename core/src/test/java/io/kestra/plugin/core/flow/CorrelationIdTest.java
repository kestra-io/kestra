package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.LoadFlows;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class CorrelationIdTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;
    @Inject
    private RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/subflow-parent.yaml",
        "flows/valids/subflow-child.yaml",
        "flows/valids/subflow-grand-child.yaml"})
    void shouldHaveCorrelationId() throws QueueException, TimeoutException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicReference<Execution> child = new AtomicReference<>();
        AtomicReference<Execution> grandChild = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("subflow-child") && execution.getState().getCurrent().isTerminated()) {
                child.set(execution);
                countDownLatch.countDown();
            }
            if (execution.getFlowId().equals("subflow-grand-child") && execution.getState().getCurrent().isTerminated()) {
                grandChild.set(execution);
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "subflow-parent");
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receive.blockLast();

        assertThat(child.get(), notNullValue());
        assertThat(child.get().getState().getCurrent(), is(State.Type.SUCCESS));
        Optional<Label> correlationId = child.get().getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent(), is(true));
        assertThat(correlationId.get().value(), is(execution.getId()));

        assertThat(grandChild.get(), notNullValue());
        assertThat(grandChild.get().getState().getCurrent(), is(State.Type.SUCCESS));
        correlationId = grandChild.get().getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent(), is(true));
        assertThat(correlationId.get().value(), is(execution.getId()));
    }
}
