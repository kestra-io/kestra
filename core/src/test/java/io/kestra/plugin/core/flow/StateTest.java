package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class StateTest extends AbstractMemoryRunnerTest {
    @SuppressWarnings("unchecked")
    @Test
    void set() throws TimeoutException {
        String stateName = IdUtils.create();

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(((Map<String, Integer>) execution.findTaskRunsByTaskId("createGet").getFirst().getOutputs().get("data")).get("value"), is(1));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(((Map<String, Object>) execution.findTaskRunsByTaskId("updateGet").getFirst().getOutputs().get("data")).get("value"), is("2"));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("deleteGet").getFirst().getOutputs().get("count"), is(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void each() throws TimeoutException, InternalException {

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", "each"));
        assertThat(execution.getTaskRunList(), hasSize(17));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(((Map<String, String>)execution.findTaskRunByTaskIdAndValue("regetEach1", List.of("b")).getOutputs().get("data")).get("value"), is("null-b"));
        assertThat(((Map<String, String>)execution.findTaskRunByTaskIdAndValue("regetEach2", List.of("b")).getOutputs().get("data")).get("value"), is("null-a-b"));
    }
}