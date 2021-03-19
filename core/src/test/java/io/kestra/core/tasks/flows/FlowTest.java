package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.utils.TestsUtils;

import java.util.Map;
import java.util.Optional;
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

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @SuppressWarnings("unchecked")
    @Test
    void run() throws Exception {
        Flow flow = Flow.builder()
            .id("unit-test")
            .type(Flow.class.getName())
            .namespace("{{inputs.namespace}}")
            .flowId("{{inputs.flow}}")
            .inputs(InputsTest.inputs)
            .wait(true)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, flow, ImmutableMap.of(
            "namespace", "io.kestra.tests",
            "flow", "inputs"
        ));

        Flow.Output run = flow.run(runContext);

        assertThat(run.getExecutionId() == null, is(false));
        assertThat(run.getState(), is(State.Type.SUCCESS));

        Optional<Execution> execution = executionRepository.findById(run.getExecutionId());

        assertThat(execution.isPresent(), is(true));
        assertThat(execution.get().getTrigger().getType(), is(Flow.class.getName()));
        assertThat(execution.get().getTrigger().getVariables().get("executionId"), is(((Map<String, String>) runContext.getVariables().get("execution")).get("id")));
        assertThat(execution.get().getTrigger().getVariables().get("flowId"), is(((Map<String, String>) runContext.getVariables().get("flow")).get("id")));
        assertThat(execution.get().getTrigger().getVariables().get("namespace"), is(((Map<String, String>) runContext.getVariables().get("flow")).get("namespace")));
    }
}
