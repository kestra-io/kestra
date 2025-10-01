package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test case for issue #8143: Parent flow shows FAILED when both parallel subflows succeed after retry
 */
@Singleton
public class ParallelSubflowRetryCaseTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    /**
     * Test that parent flow shows SUCCESS when both parallel subflows eventually succeed after retrying.
     * This reproduces the exact scenario from issue #8143.
     */
    public void parentFlowShouldSucceedWhenBothSubflowsSucceedAfterRetry(String tenantId) throws Exception {
        // Run the main flow with both 'Inbox' and 'Sent' selected
        Execution execution = runnerUtils.runOne(
            tenantId,
            "io.kestra.tests",
            "mainflow-issue-8143",
            null,
            (f, e) -> Map.of("box", List.of("Inbox", "Sent"))
        );

        // The parent flow should be SUCCESS because both subflows eventually succeed after retry
        assertThat(execution.getState().getCurrent())
            .as("Parent flow should be SUCCESS when both parallel subflows succeed after retrying")
            .isEqualTo(State.Type.SUCCESS);

        // Verify the foreach/parallel task succeeded
        assertThat(execution.getTaskRunList()).isNotEmpty();
        assertThat(execution.findTaskRunsByTaskId("foreach").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
    }

    /**
     * Test that parent flow shows SUCCESS when only one subflow is selected and succeeds after retry.
     */
    public void parentFlowShouldSucceedWhenOnlyInboxSubflowSucceedsAfterRetry(String tenantId) throws Exception {
        // Run the main flow with only 'Inbox' selected
        Execution execution = runnerUtils.runOne(
            tenantId,
            "io.kestra.tests",
            "mainflow-issue-8143",
            null,
            (f, e) -> Map.of("box", List.of("Inbox"))
        );

        // The parent flow should be SUCCESS
        assertThat(execution.getState().getCurrent())
            .as("Parent flow should be SUCCESS when single subflow succeeds after retrying")
            .isEqualTo(State.Type.SUCCESS);
    }

    /**
     * Test that parent flow shows SUCCESS when only 'Sent' subflow is selected and succeeds after retry.
     */
    public void parentFlowShouldSucceedWhenOnlySentSubflowSucceedsAfterRetry(String tenantId) throws Exception {
        // Run the main flow with only 'Sent' selected
        Execution execution = runnerUtils.runOne(
            tenantId,
            "io.kestra.tests",
            "mainflow-issue-8143",
            null,
            (f, e) -> Map.of("box", List.of("Sent"))
        );

        // The parent flow should be SUCCESS
        assertThat(execution.getState().getCurrent())
            .as("Parent flow should be SUCCESS when single subflow succeeds after retrying")
            .isEqualTo(State.Type.SUCCESS);
    }
}
