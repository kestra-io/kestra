package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;

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

        Execution execution = runnerUtils.runOne("io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(((Map<String, Integer>) execution.findTaskRunsByTaskId("createGet").get(0).getOutputs().get("data")).get("value"), is(1));

        execution = runnerUtils.runOne("io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(((Map<String, Object>) execution.findTaskRunsByTaskId("updateGet").get(0).getOutputs().get("data")).get("value"), is("2"));

        execution = runnerUtils.runOne("io.kestra.tests", "state",  null, (f, e) -> ImmutableMap.of("state", stateName));
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("deleteGet").get(0).getOutputs().get("count"), is(0));
    }
}