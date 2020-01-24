package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.runners.RunContext;
import org.kestra.core.utils.Await;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;


class FlowTest extends AbstractMemoryRunnerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    QueueInterface<Execution> executionQueue;

    private Execution awaitExecution(org.kestra.core.models.flows.Flow flow, Supplier<String> emitExecution, Duration duration) throws TimeoutException {
        AtomicReference<String> executionId = new AtomicReference<>();
        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(current -> {
            if (current.getId().equals(executionId.get()) && current.isTerminatedWithListeners(flow)) {
                receive.set(current);
            }
        });

        executionId.set(emitExecution.get());

        Await.until(() -> receive.get() != null, null, duration);

        cancel.run();

        return receive.get();
    }

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "namespace", "org.kestra.tests",
                "flow", "inputs"
            )
        );

        Execution execution = this.awaitExecution(
            flowRepositoryInterface.findById("org.kestra.tests", "inputs").get(),
            () -> {
                Flow flow = Flow.builder()
                    .namespace("{{namespace}}")
                    .flowId("{{flow}}")
                    .inputs(InputsTest.inputs)
                    .build();

                Flow.Output run = null;
                try {
                    run = flow.run(runContext);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                assertThat(run.getExecutionId() == null, is(false));

                return run.getExecutionId();
            }, Duration.ofSeconds(5)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}