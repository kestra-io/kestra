package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FlowOutputTest extends AbstractMemoryRunnerTest {

    static final String NAMESPACE = "io.kestra.tests";

    @Test
    void shouldGetSuccessExecutionForFlowWithOutputs() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, NAMESPACE, "flow-with-outputs", null, null);
        assertThat(execution.getOutputs(), aMapWithSize(1));
        assertThat(execution.getOutputs().get("key"), is("{\"value\":\"flow-with-outputs\"}"));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetSuccessExecutionForFlowWithArrayOutputs() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, NAMESPACE, "flow-with-array-outputs", null, null);
        assertThat(execution.getOutputs(), aMapWithSize(1));
        assertThat((List<String>) execution.getOutputs().get("myout"), hasItems("1rstValue", "2ndValue"));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void shouldGetFailExecutionForFlowWithInvalidOutputs() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, NAMESPACE, "flow-with-outputs-failed", null, null);
        assertThat(execution.getOutputs(), nullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
