package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
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

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "namespace", "org.kestra.tests",
                "flow", "inputs"
            )
        );

        Flow flow = Flow.builder()
            .namespace("{{namespace}}")
            .flowId("{{flow}}")
            .inputs(InputsTest.inputs)
            .wait(true)
            .build();

        Flow.Output run = flow.run(runContext);

        assertThat(run.getExecutionId() == null, is(false));
        assertThat(run.getState(), is(State.Type.SUCCESS));
    }
}
