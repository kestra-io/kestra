package io.kestra.core.runners;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class SLATestCase {
    @Inject
    private TestRunnerUtils runnerUtils;

    public void maxDurationSLAShouldFail() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-max-duration-fail");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void maxDurationSLAShouldPass() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-max-duration-ok");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void executionConditionSLAShouldPass() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-execution-condition");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void executionConditionSLAShouldCancel(String tenantId) throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "sla-execution-condition", null, (f, e) -> Map.of("string", "CANCEL"));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
    }

    public void executionConditionSLAShouldLabel(String tenantId) throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "sla-execution-condition", null, (f, e) -> Map.of("string", "LABEL"));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getLabels()).contains(new Label("sla", "violated"));
    }

    public void slaViolationOnSubflowMayEndTheParentFlow() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-parent-flow");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
