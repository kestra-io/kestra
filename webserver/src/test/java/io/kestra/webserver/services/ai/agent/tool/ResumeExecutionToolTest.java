package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class ResumeExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private ResumeExecutionTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @LoadFlows({ "flows/valids/pause_on_resume_optional.yaml" })
    void shouldResumeWithOnResumeInputsWhenExecutionIsPaused() throws Exception {
        // Given
        Execution paused = runnerUtils.runOneUntilPaused(MAIN_TENANT, NAMESPACE, "pause_on_resume_optional");

        // When
        Map<String, Object> result = CatalogDispatcher.dispatch(
            tool, "resume_execution", """
                { "executionId": "%s", "inputs": { "asked": "from-the-agent" } }""".formatted(paused.getId()), MAIN_TENANT
        );

        // Then
        assertThat(result).containsEntry("executionId", paused.getId());
        Execution terminated = runnerUtils.awaitExecution(execution -> execution.getState().isTerminated(), paused);
        assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = taskOutputService.getOutputs(terminated.findTaskRunsByTaskId("last").getFirst());
        assertThat(outputs).containsEntry("values", Map.of("asked", "from-the-agent"));
    }

    @Test
    @LoadFlows({ "flows/valids/pause_on_resume_optional.yaml" })
    void shouldRefuseResumeWhenExecutionIsNotPaused() throws Exception {
        Execution paused = runnerUtils.runOneUntilPaused(MAIN_TENANT, NAMESPACE, "pause_on_resume_optional");
        CatalogDispatcher.dispatch(tool, "resume_execution", "{ \"executionId\": \"%s\" }".formatted(paused.getId()), MAIN_TENANT);
        runnerUtils.awaitExecution(execution -> execution.getState().isTerminated(), paused);

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(tool, "resume_execution", "{ \"executionId\": \"%s\" }".formatted(paused.getId()), MAIN_TENANT)
        ).hasMessageContaining("it is not paused");
    }
}
