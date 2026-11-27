package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class UpdateExecutionLabelsToolTest {

    @Inject
    private UpdateExecutionLabelsTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    @SuppressWarnings("unchecked")
    void shouldMergeAddedAndRemovedLabelsKeepingTheOthers() throws Exception {
        Execution ended = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests", "minimal", null, null, null,
            List.of(new Label("team", "data"), new Label("stale", "yes"), new Label("kept", "1"))
        );

        Map<String, Object> result = ToolCalls.call(tool, "update_execution_labels", """
            { "executionId": "%s", "labels": { "team": "platform", "env": "prod" }, "removeKeys": ["stale"] }
            """.formatted(ended.getId()), MAIN_TENANT);

        assertThat((Map<String, Object>) result.get("labels"))
            .containsEntry("team", "platform").containsEntry("env", "prod").containsEntry("kept", "1").doesNotContainKey("stale");
        Execution updated = awaitLabel(ended, "env");
        assertThat(updated.getLabels())
            .contains(new Label("team", "platform"), new Label("env", "prod"), new Label("kept", "1"))
            .noneMatch(label -> "stale".equals(label.key()))
            .anyMatch(label -> Label.isSystem(label.key()));
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void shouldRefuseSystemLabels() throws Exception {
        Execution ended = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        assertThatThrownBy(() -> ToolCalls.call(tool, "update_execution_labels", """
            { "executionId": "%s", "removeKeys": ["%s"] }
            """.formatted(ended.getId(), Label.FROM), MAIN_TENANT))
            .hasMessageContaining("System labels");
    }

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldRefuseWhenExecutionHasNotEnded() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");

        assertThatThrownBy(() -> ToolCalls.call(tool, "update_execution_labels", """
            { "executionId": "%s", "labels": { "team": "data" } }
            """.formatted(running.getId()), MAIN_TENANT))
            .hasMessageContaining("ended execution");
    }

    private Execution awaitLabel(final Execution execution, final String key) {
        return runnerUtils.awaitExecution(
            candidate -> candidate.getLabels().stream().anyMatch(label -> key.equals(label.key())),
            execution,
            Duration.ofSeconds(15)
        );
    }
}
