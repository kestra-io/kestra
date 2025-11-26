package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class SubflowRunnerTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

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

    @Test
    @LoadFlows({"flows/valids/subflow-parent-no-wait.yaml", "flows/valids/subflow-child-with-output.yaml"})
    void subflowOutputWithoutWait() throws QueueException, TimeoutException, InterruptedException {
        AtomicReference<Execution> childExecution = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Runnable closing = executionQueue.receive(either -> {
            if (either.isLeft() && either.getLeft().getFlowId().equals("subflow-child-with-output") && either.getLeft().getState().isTerminated()) {
                childExecution.set(either.getLeft());
                countDownLatch.countDown();
            }
        });

        Execution parentExecution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "subflow-parent-no-wait");
        String childExecutionId = (String) parentExecution.findTaskRunsByTaskId("subflow").getFirst().getOutputs().get("executionId");
        assertThat(childExecutionId).isNotBlank();
        assertThat(parentExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(parentExecution.getTaskRunList()).hasSize(1);

        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        assertThat(childExecution.get().getId()).isEqualTo(childExecutionId);
        assertThat(childExecution.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(childExecution.get().getTaskRunList()).hasSize(1);
        closing.run();
    }

    @Test
    @LoadFlows({"flows/valids/subflow-parent-retry.yaml", "flows/valids/subflow-to-retry.yaml"})
    void subflowOutputWithWait() throws QueueException, TimeoutException, InterruptedException {
        List<Execution> childExecution = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(4);
        Runnable closing = executionQueue.receive(either -> {
            if (either.isLeft() && either.getLeft().getFlowId().equals("subflow-to-retry") && either.getLeft().getState().isTerminated()) {
                childExecution.add(either.getLeft());
                countDownLatch.countDown();
            }
        });

        Execution parentExecution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "subflow-parent-retry");
        assertThat(parentExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(parentExecution.getTaskRunList()).hasSize(5);

        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        // we should have 4 executions, two in SUCCESS and two in FAILED
        assertThat(childExecution).hasSize(4);
        assertThat(childExecution.stream().filter(e -> e.getState().getCurrent() == State.Type.SUCCESS).count()).isEqualTo(2);
        assertThat(childExecution.stream().filter(e -> e.getState().getCurrent() == State.Type.FAILED).count()).isEqualTo(2);
        closing.run();
    }
}
