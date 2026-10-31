package io.kestra.core.runners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.DateUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class SLATestCase {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    public void maxDurationSLAShouldFail() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-max-duration-fail");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Every task run (and its last attempt) must be in a terminal state so the UI
        // does not display a stuck "still running" duration after an SLA FAIL.
        List<TaskRun> taskRuns = execution.getTaskRunList();
        if (taskRuns != null) {
            for (TaskRun taskRun : taskRuns) {
                assertThat(taskRun.getState().isTerminated())
                    .as("taskRun '%s' must be terminal after SLA FAIL", taskRun.getTaskId())
                    .isTrue();
                List<TaskRunAttempt> attempts = taskRun.getAttempts();
                if (attempts != null && !attempts.isEmpty()) {
                    TaskRunAttempt lastAttempt = attempts.getLast();
                    assertThat(lastAttempt.getState().isTerminated())
                        .as("last attempt of taskRun '%s' must be terminal after SLA FAIL", taskRun.getTaskId())
                        .isTrue();
                }
            }
        }
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

        // Every task run (and its last attempt) must be in a terminal state so the UI
        // does not display a stuck "still running" duration after an SLA CANCEL.
        List<TaskRun> taskRuns = execution.getTaskRunList();
        if (taskRuns != null) {
            for (TaskRun taskRun : taskRuns) {
                assertThat(taskRun.getState().isTerminated())
                    .as("taskRun '%s' must be terminal after SLA CANCEL", taskRun.getTaskId())
                    .isTrue();
                List<TaskRunAttempt> attempts = taskRun.getAttempts();
                if (attempts != null && !attempts.isEmpty()) {
                    TaskRunAttempt lastAttempt = attempts.getLast();
                    assertThat(lastAttempt.getState().isTerminated())
                        .as("last attempt of taskRun '%s' must be terminal after SLA CANCEL", taskRun.getTaskId())
                        .isTrue();
                }
            }
        }
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

    public void shouldStopTasksButRunAfterExecutionWhenExecutionAssertionFails() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-execution-assertion-after-execution");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).extracting(TaskRun::getTaskId).containsExactly("check", "end");
        assertThat(execution.findTaskRunsByTaskId("end").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void shouldNotKillFlowTriggeredExecutionWhenSLAFails() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-flow-trigger-source");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution listener = runnerUtils.awaitFlowExecution(
            e -> e.getState().isTerminated(), MAIN_TENANT, "io.kestra.tests", "sla-flow-trigger-listener"
        );
        assertThat(listener.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(listener.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void avgDurationSLAShouldFail() throws QueueException, TimeoutException {
        String flowId = "sla-avg-duration-fail";
        for (int i = 0; i < 3; i++) {
            Execution history = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", flowId);
            assertThat(history.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }
        awaitExecutionStatistics(flowId, 3);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", flowId, null, (f, e) -> Map.of("sleep", "PT3S"));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void avgDurationSLAShouldNotMonitorWithoutEnoughHistory() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "sla-avg-duration-not-enough-history");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    private void awaitExecutionStatistics(String flowId, long expected) throws TimeoutException {
        Await.until(
            () -> executionStatisticsRepository
                .statistics(MAIN_TENANT, "io.kestra.tests", flowId, List.of(State.Type.SUCCESS), Instant.now().minus(Duration.ofDays(1)), Instant.now(), DateUtils.GroupType.MONTH)
                .stream()
                .mapToLong(it -> it.getDuration().getCount())
                .sum() >= expected,
            Duration.ofMillis(100),
            Duration.ofSeconds(30)
        );
    }
}
