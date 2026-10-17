package io.kestra.core.models.executions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failed child is final once the task run the executor spends its retries on has no attempt left: the child's own
 * task run under a Sequential, the WorkingDirectory's task run for a child of a WorkingDirectory.
 */
class ExecutionRetryFinalityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final Execution execution = Execution.builder()
        .id(IdUtils.create())
        .namespace("io.kestra.tests")
        .flowId("retry-finality")
        .state(new State())
        .build();

    @Test
    void shouldBeFinalWhenTheWorkingDirectoryHasSpentTheChildAttempts() throws Exception {
        // Given: two runs of the WorkingDirectory, the child allows two attempts and failed once in the latest run
        AbstractRetry retry = Constant.builder().interval(Duration.ofSeconds(1)).maxAttempts(2).build();
        WorkingDirectory workingDirectory = WorkingDirectory.builder().id("working-directory").type(WorkingDirectory.class.getName()).tasks(List.of(child(retry))).build();
        TaskRun parent = TaskRun.of(execution, ResolvedTask.of(workingDirectory))
            .withAttempts(List.of(attempt(T0, T0.plusSeconds(1), State.Type.RUNNING), attempt(T0.plusSeconds(2), T0.plusSeconds(3), State.Type.RUNNING)))
            .withState(State.Type.RUNNING);
        List<ResolvedTask> childTasks = workingDirectory.childTasks(null, parent);
        TaskRun failedChild = TaskRun.of(execution, childTasks.getFirst())
            .withAttempts(List.of(attempt(T0.plusSeconds(2), T0.plusSeconds(3), State.Type.FAILED)))
            .withState(State.Type.FAILED);
        Execution snapshot = execution.withTaskRunList(List.of(parent, failedChild));

        // Then: final, as the executor would not schedule another run either
        assertThat(parent.nextRetryDate(retry)).isNull();
        assertThat(snapshot.hasFailedNoRetry(childTasks, parent)).isTrue();
    }

    @Test
    void shouldNotBeFinalWhenTheChildOwnsItsAttemptsUnderASequential() throws Exception {
        // Given: the same shape under a Sequential, where the child's own task run accumulates the attempts
        AbstractRetry retry = Constant.builder().interval(Duration.ofSeconds(1)).maxAttempts(2).build();
        Sequential sequential = Sequential.builder().id("sequential").type(Sequential.class.getName()).tasks(List.of(child(retry))).build();
        TaskRun parent = TaskRun.of(execution, ResolvedTask.of(sequential))
            .withAttempts(List.of(attempt(T0, T0.plusSeconds(1), State.Type.RUNNING), attempt(T0.plusSeconds(2), T0.plusSeconds(3), State.Type.RUNNING)))
            .withState(State.Type.RUNNING);
        List<ResolvedTask> childTasks = sequential.childTasks(null, parent);
        TaskRun failedChild = TaskRun.of(execution, childTasks.getFirst())
            .withAttempts(List.of(attempt(T0.plusSeconds(2), T0.plusSeconds(3), State.Type.FAILED)))
            .withState(State.Type.FAILED);
        Execution snapshot = execution.withTaskRunList(List.of(parent, failedChild));

        assertThat(childTasks.getFirst().getRetryBudgetOwner()).isEqualTo(ResolvedTask.RetryBudgetOwner.TASK_RUN);
        assertThat(failedChild.nextRetryDate(retry)).isNotNull();
        assertThat(snapshot.hasFailedNoRetry(childTasks, parent)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "PT4S, false", "PT5S, false", "PT5.001S, true" })
    void shouldCountTheChildMaxDurationFromTheFirstRunOfTheWorkingDirectory(Duration lastRunEndsAfter, boolean expectedFinal) throws Exception {
        // Given: the next retry would fire 5 seconds after the latest run ended, within 10 seconds of the first run
        AbstractRetry retry = Constant.builder().interval(Duration.ofSeconds(5)).maxDuration(Duration.ofSeconds(10)).build();
        WorkingDirectory workingDirectory = WorkingDirectory.builder().id("working-directory").type(WorkingDirectory.class.getName()).tasks(List.of(child(retry))).build();
        Instant lastRunEnd = T0.plus(lastRunEndsAfter);
        TaskRun parent = TaskRun.of(execution, ResolvedTask.of(workingDirectory))
            .withAttempts(List.of(attempt(T0, T0.plusSeconds(1), State.Type.RUNNING), attempt(T0.plusSeconds(2), lastRunEnd, State.Type.RUNNING)))
            .withState(State.Type.RUNNING);
        List<ResolvedTask> childTasks = workingDirectory.childTasks(null, parent);
        TaskRun failedChild = TaskRun.of(execution, childTasks.getFirst())
            .withAttempts(List.of(attempt(T0.plusSeconds(2), lastRunEnd, State.Type.FAILED)))
            .withState(State.Type.FAILED);
        Execution snapshot = execution.withTaskRunList(List.of(parent, failedChild));

        // Then: a retry exactly at the deadline is still allowed, and finality agrees with the scheduled retry date
        assertThat(parent.nextRetryDate(retry) == null).isEqualTo(expectedFinal);
        assertThat(snapshot.hasFailedNoRetry(childTasks, parent)).isEqualTo(expectedFinal);
    }

    private static Task child(AbstractRetry retry) {
        return Return.builder().id("child").type(Return.class.getName()).format(Property.ofValue("child")).retry(retry).build();
    }

    private static TaskRunAttempt attempt(Instant start, Instant end, State.Type state) {
        return TaskRunAttempt.builder()
            .state(new State(state, List.of(new State.History(State.Type.CREATED, start), new State.History(state, end))))
            .build();
    }
}
