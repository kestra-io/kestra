package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionMetadataTest {

    @Test
    void shouldAccumulateTaskRunCountAndDurationWhenTaskRunsAreRemoved() {
        ExecutionMetadata metadata = ExecutionMetadata.builder()
            .originalCreatedDate(Instant.now())
            .build();

        TaskRun taskRun1 = TaskRun.builder()
            .tenantId("test")
            .id("tr-1")
            .executionId("exec-1")
            .namespace("ns")
            .flowId("flow")
            .taskId("task1")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .build();

        TaskRun taskRun2 = TaskRun.builder()
            .tenantId("test")
            .id("tr-2")
            .executionId("exec-1")
            .namespace("ns")
            .flowId("flow")
            .taskId("task2")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .build();

        long expectedDuration = taskRun1.getState().getDurationOrComputeIt().toMillis()
            + taskRun2.getState().getDurationOrComputeIt().toMillis();

        ExecutionMetadata result = metadata.accumulateRemovedTaskRuns(List.of(taskRun1, taskRun2));

        assertThat(result).isNotNull();
        assertThat(result.getAccumulatedTaskRunCount()).isEqualTo(2);
        assertThat(result.getAccumulatedTaskRunDurationSumMs()).isEqualTo(expectedDuration);
    }

    @Test
    void shouldAccumulateAcrossMultipleCallsWhenCalledRepeatedly() {
        ExecutionMetadata metadata = ExecutionMetadata.builder()
            .originalCreatedDate(Instant.now())
            .accumulatedTaskRunCount(3)
            .accumulatedTaskRunDurationSumMs(5000)
            .build();

        TaskRun taskRun = TaskRun.builder()
            .tenantId("test")
            .id("tr-1")
            .executionId("exec-1")
            .namespace("ns")
            .flowId("flow")
            .taskId("task1")
            .state(new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS))
            .build();

        long taskRunDuration = taskRun.getState().getDurationOrComputeIt().toMillis();

        ExecutionMetadata result = metadata.accumulateRemovedTaskRuns(List.of(taskRun));

        assertThat(result).isNotNull();
        assertThat(result.getAccumulatedTaskRunCount()).isEqualTo(4);
        assertThat(result.getAccumulatedTaskRunDurationSumMs()).isEqualTo(5000 + taskRunDuration);
    }

    @Test
    void shouldReturnSameValuesWhenEmptyListPassed() {
        ExecutionMetadata metadata = ExecutionMetadata.builder()
            .originalCreatedDate(Instant.now())
            .build();

        ExecutionMetadata result = metadata.accumulateRemovedTaskRuns(Collections.emptyList());

        assertThat(result).isNotNull();
        assertThat(result.getAccumulatedTaskRunCount()).isEqualTo(0);
        assertThat(result.getAccumulatedTaskRunDurationSumMs()).isEqualTo(0);
    }
}
