package io.kestra.core.models.executions.statistics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionMetadata;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionStatisticTest {

    @Test
    void shouldIncludeAccumulatedTaskRunCountWhenMetadataHasAccumulatedStats() {
        TaskRun taskRun1 = createTaskRun("tr-1");
        TaskRun taskRun2 = createTaskRun("tr-2");

        Execution baseExecution = Execution.builder()
            .tenantId("test")
            .id("exec-1")
            .namespace("ns")
            .flowId("flow")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .taskRunList(List.of(taskRun1, taskRun2))
            .build();

        Execution execution = baseExecution.withMetadata(ExecutionMetadata.builder()
            .originalCreatedDate(Instant.now())
            .accumulatedTaskRunCount(5)
            .accumulatedTaskRunDurationSumMs(3000)
            .build());

        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        ExecutionStatistic stat = new ExecutionStatistic(execution, bucket);

        assertThat(stat).isNotNull();
        assertThat(stat.taskRunCount()).isEqualTo(7L);
        assertThat(stat.taskRunsDurationSumMs()).isGreaterThanOrEqualTo(3000L);
    }

    @Test
    void shouldComputeCorrectStatsWhenNoAccumulatedMetadata() {
        TaskRun taskRun1 = createTaskRun("tr-1");
        TaskRun taskRun2 = createTaskRun("tr-2");
        TaskRun taskRun3 = createTaskRun("tr-3");

        Execution execution = Execution.builder()
            .tenantId("test")
            .id("exec-1")
            .namespace("ns")
            .flowId("flow")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .taskRunList(List.of(taskRun1, taskRun2, taskRun3))
            .build();

        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        ExecutionStatistic stat = new ExecutionStatistic(execution, bucket);

        assertThat(stat).isNotNull();
        assertThat(stat.taskRunCount()).isEqualTo(3L);
    }

    @Test
    void shouldComputeCorrectStatsWhenMetadataIsNull() {
        TaskRun taskRun1 = createTaskRun("tr-1");

        Execution baseExecution = Execution.builder()
            .tenantId("test")
            .id("exec-1")
            .namespace("ns")
            .flowId("flow")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .taskRunList(List.of(taskRun1))
            .build();

        Execution execution = baseExecution.withMetadata(null);

        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        ExecutionStatistic stat = new ExecutionStatistic(execution, bucket);

        assertThat(stat).isNotNull();
        assertThat(stat.taskRunCount()).isEqualTo(1L);
    }

    private TaskRun createTaskRun(String id) {
        return TaskRun.builder()
            .tenantId("test")
            .id(id)
            .executionId("exec-1")
            .namespace("ns")
            .flowId("flow")
            .taskId("task1")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .build();
    }
}
