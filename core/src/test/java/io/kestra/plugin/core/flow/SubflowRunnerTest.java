package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class SubflowRunnerTest {

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows({"flows/valids/subflow-inherited-labels-child.yaml", "flows/valids/subflow-inherited-labels-parent.yaml"})
    void inheritedLabelsAreOverridden() throws QueueException, TimeoutException {
        Execution parentExecution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "subflow-inherited-labels-parent");

        assertThat(parentExecution.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, parentExecution.getId()),
            new Label("parentFlowLabel1", "value1"),
            new Label("parentFlowLabel2", "value2")
        );

        String childExecutionId = (String) parentExecution.findTaskRunsByTaskId("launch").getFirst().getOutputs().get("executionId");

        assertThat(childExecutionId).isNotBlank();

        Execution childExecution = executionRepository.findById(MAIN_TENANT, childExecutionId).orElseThrow();

        assertThat(childExecution.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, parentExecution.getId()), // parent's correlation ID
            new Label("childFlowLabel1", "value1"), // defined by the subtask flow
            new Label("childFlowLabel2", "value2"), // defined by the subtask flow
            new Label("launchTaskLabel", "launchFoo"), // added by Subtask
            new Label("parentFlowLabel1", "launchBar"), // overridden by Subtask
            new Label("parentFlowLabel2", "value2") // inherited from the parent flow
        );
    }
}
