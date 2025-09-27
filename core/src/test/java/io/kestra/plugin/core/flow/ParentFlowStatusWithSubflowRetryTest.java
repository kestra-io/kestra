package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class ParentFlowStatusWithSubflowRetryTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    void parentFlowShouldSucceedWhenBothSubflowsSucceedAfterRetry() throws Exception {
        // Test the scenario where both 'Inbox' and 'Sent' are selected and both subflows succeed after retry
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "parent-flow-multiselect-retry",
            null,
            (f, e) -> Map.of("box", java.util.List.of("Inbox", "Sent"))
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1); // The Parallel task
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void parentFlowShouldSucceedWhenOnlyInboxSubflowSucceedsAfterRetry() throws Exception {
        // Test the scenario where only 'Inbox' is selected and the subflow succeeds after retry
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "parent-flow-multiselect-retry",
            null,
            (f, e) -> Map.of("box", java.util.List.of("Inbox"))
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1); // The Parallel task
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void parentFlowShouldSucceedWhenOnlySentSubflowSucceedsAfterRetry() throws Exception {
        // Test the scenario where only 'Sent' is selected and the subflow succeeds after retry
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "parent-flow-multiselect-retry",
            null,
            (f, e) -> Map.of("box", java.util.List.of("Sent"))
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1); // The Parallel task
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}