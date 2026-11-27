package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ReadExecutionOutputsToolTest {

    @Inject
    private ReadExecutionOutputsTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @SuppressWarnings("unchecked")
    @LoadFlows({ "flows/ai-tools/greeting.yaml" })
    void shouldReturnFlowAndTaskOutputsAndShortenLargeOnes() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.ai", "greeting", null, (flow, created) -> Map.of("name", "Ada"));

        Map<String, Object> result = ToolCalls.call(tool, "read_execution_outputs", """
            { "executionId": "%s" }
            """.formatted(execution.getId()), MAIN_TENANT);

        assertThat((Map<String, Object>) result.get("flowOutputs")).containsEntry("greeting", "hello Ada");
        List<Map<String, Object>> taskRuns = taskRuns(result);
        assertThat(taskRuns).anySatisfy(taskRun -> {
            assertThat(taskRun).containsEntry("taskId", "say");
            assertThat((Map<String, Object>) taskRun.get("outputs")).containsEntry("value", "hello Ada");
        });
        assertThat(taskRuns).anySatisfy(taskRun -> {
            assertThat(taskRun).containsEntry("taskId", "big");
            assertThat(taskRun.get("outputs")).asString().hasSize(ReadExecutionOutputsTool.MAX_OUTPUT_CHARS);
        });
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    @LoadFlows({ "flows/ai-tools/greeting.yaml" })
    void shouldOnlyReturnTheRequestedTask() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.ai", "greeting", null, (flow, created) -> Map.of("name", "Ada"));

        Map<String, Object> result = ToolCalls.call(tool, "read_execution_outputs", """
            { "executionId": "%s", "taskId": "say" }
            """.formatted(execution.getId()), MAIN_TENANT);

        assertThat(taskRuns(result)).singleElement().satisfies(taskRun -> assertThat(taskRun).containsEntry("taskId", "say"));
        assertThat(result).containsEntry("truncated", false);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> taskRuns(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("taskRuns");
    }
}
