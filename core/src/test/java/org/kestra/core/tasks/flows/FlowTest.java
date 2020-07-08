package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.*;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FlowTest extends AbstractMemoryRunnerTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    QueueInterface<Execution> executionQueue;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of(
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
