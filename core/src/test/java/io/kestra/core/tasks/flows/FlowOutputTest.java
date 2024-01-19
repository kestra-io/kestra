package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class FlowOutputTest extends AbstractMemoryRunnerTest {

    static final String NAMESPACE = "io.kestra.tests";

    @Test
    void shouldGetSuccessExecutionForFlowFlowWithOutputs() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, NAMESPACE, "flow-with-outputs", null, null);
        assertThat(execution.getOutputs(), aMapWithSize(1));
        assertThat(execution.getOutputs().get("key"), is("{\"value\":\"flow-with-outputs\"}"));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void shouldGetFailExecutionForFlowWithInvalidOutputs() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, NAMESPACE, "flow-with-outputs-failed", null, null);
        assertThat(execution.getOutputs(), nullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
