package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
@Property(name = "kestra.ai.agent.execute-flow-max-wait", value = "PT2S")
class ExecuteFlowToolTest {

    @Inject
    private ExecuteFlowTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows({ "flows/ai-tools/greeting.yaml" })
    void shouldRunFlowWithInputsAndLabelsWhenWaiting() throws Exception {
        Map<String, Object> result = ToolCalls.call(tool, "execute_flow", """
            { "namespace": "io.kestra.tests.ai", "flowId": "greeting", "inputs": { "name": "Ada" }, "labels": { "team": "data" }, "wait": true }
            """, MAIN_TENANT);

        assertThat(result).containsEntry("state", "SUCCESS").containsEntry("terminated", true);
        Execution execution = executionRepository.findById(MAIN_TENANT, (String) result.get("executionId")).orElseThrow();
        assertThat(execution.getInputs()).containsEntry("name", "Ada");
        assertThat(execution.getLabels()).contains(new Label("team", "data"));
    }

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldReturnRunningExecutionWithoutFailingWhenWaitExceedsTheCap() throws Exception {
        Map<String, Object> result = ToolCalls.call(tool, "execute_flow", """
            { "namespace": "io.kestra.tests.ai", "flowId": "sleep", "wait": true }
            """, MAIN_TENANT);

        assertThat(result).containsEntry("terminated", false);
        assertThat(executionRepository.findById(MAIN_TENANT, (String) result.get("executionId")).orElseThrow().getState().getCurrent())
            .isIn(State.Type.CREATED, State.Type.RUNNING);
    }

    @Test
    @LoadFlows({ "flows/ai-tools/greeting.yaml" })
    void shouldNotCreateExecutionWhenRequiredInputIsMissing() {
        long before = greetingExecutionCount();

        assertThatThrownBy(() -> ToolCalls.call(tool, "execute_flow", """
            { "namespace": "io.kestra.tests.ai", "flowId": "greeting" }
            """, MAIN_TENANT))
            .hasMessageContaining("name");

        assertThat(greetingExecutionCount()).isEqualTo(before);
    }

    private long greetingExecutionCount() {
        return executionRepository.find(
            PageableUtils.from(1, 1), MAIN_TENANT,
            List.of(new QueryFilter(QueryFilter.Field.FLOW_ID, QueryFilter.Op.EQUALS, "greeting", null, null))
        ).getTotal();
    }
}
