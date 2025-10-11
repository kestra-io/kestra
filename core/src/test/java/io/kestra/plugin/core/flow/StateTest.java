package io.kestra.plugin.core.flow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class StateTest {

    public static final String FLOW_ID = "state";
    public static final String NAMESPACE = "io.kestra.tests";
    @Inject
    private TestRunnerUtils runnerUtils;

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/state.yaml"})
    void set() throws TimeoutException, QueueException {
        String stateName = IdUtils.create();

        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE,
            FLOW_ID,  null, (f, e) -> ImmutableMap.of(FLOW_ID, stateName));
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(((Map<String, Integer>) execution.findTaskRunsByTaskId("createGet").getFirst().getOutputs().get("data")).get("value")).isEqualTo(1);

        execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE,
            FLOW_ID,  null, (f, e) -> ImmutableMap.of(FLOW_ID, stateName));
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(((Map<String, Object>) execution.findTaskRunsByTaskId("updateGet").getFirst().getOutputs().get("data")).get("value")).isEqualTo("2");

        execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE,
            FLOW_ID,  null, (f, e) -> ImmutableMap.of(FLOW_ID, stateName));
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((Integer) execution.findTaskRunsByTaskId("deleteGet").getFirst().getOutputs().get("count")).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = {"flows/valids/state.yaml"}, tenantId = "tenant1")
    void each() throws TimeoutException, InternalException, QueueException {

        Execution execution = runnerUtils.runOne("tenant1", NAMESPACE,
            FLOW_ID,  null, (f, e) -> ImmutableMap.of(FLOW_ID, "each"));
        assertThat(execution.getTaskRunList()).hasSize(19);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(((Map<String, String>) execution.findTaskRunByTaskIdAndValue("regetEach1", List.of("b")).getOutputs().get("data")).get("value")).isEqualTo("null-b");
        assertThat(((Map<String, String>) execution.findTaskRunByTaskIdAndValue("regetEach2", List.of("b")).getOutputs().get("data")).get("value")).isEqualTo("null-a-b");
    }
}