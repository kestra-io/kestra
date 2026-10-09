package io.kestra.core.models.executions.statistics;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunStateCountsTest {
    private static TaskRun taskRun(String taskId, State.Type state) {
        return TaskRun.builder().id(taskId + state).taskId(taskId).state(new State(state)).build();
    }

    @Test
    void shouldCountTaskRunsByTaskIdAndState() {
        // Given
        List<TaskRun> taskRuns = List.of(
            taskRun("a", State.Type.SUCCESS),
            taskRun("a", State.Type.SUCCESS),
            taskRun("a", State.Type.FAILED),
            taskRun("b", State.Type.SUCCESS)
        );

        // When
        TaskRunStateCounts counts = TaskRunStateCounts.of(taskRuns);

        // Then
        assertThat(counts.counts()).containsEntry("a", Map.of(State.Type.SUCCESS, 2L, State.Type.FAILED, 1L));
        assertThat(counts.counts()).containsEntry("b", Map.of(State.Type.SUCCESS, 1L));
    }

    @Test
    void shouldReturnEmptyWhenNoTaskRuns() {
        assertThat(TaskRunStateCounts.of(null).isEmpty()).isTrue();
        assertThat(TaskRunStateCounts.of(List.of()).isEmpty()).isTrue();
    }

    @Test
    void shouldSumCountsWhenAdding() {
        // Given
        TaskRunStateCounts first = TaskRunStateCounts.of(List.of(taskRun("a", State.Type.SUCCESS), taskRun("b", State.Type.FAILED)));
        TaskRunStateCounts second = TaskRunStateCounts.of(List.of(taskRun("a", State.Type.SUCCESS), taskRun("a", State.Type.FAILED)));

        // When
        TaskRunStateCounts sum = first.plus(second);

        // Then
        assertThat(sum.counts()).containsEntry("a", Map.of(State.Type.SUCCESS, 2L, State.Type.FAILED, 1L));
        assertThat(sum.counts()).containsEntry("b", Map.of(State.Type.FAILED, 1L));
        assertThat(first.counts()).containsEntry("a", Map.of(State.Type.SUCCESS, 1L));
    }

    @Test
    void shouldIgnoreNullOrEmptyWhenAdding() {
        // Given
        TaskRunStateCounts counts = TaskRunStateCounts.of(List.of(taskRun("a", State.Type.SUCCESS)));

        // When / Then
        assertThat(counts.plus(null)).isSameAs(counts);
        assertThat(counts.plus(TaskRunStateCounts.empty())).isSameAs(counts);
        assertThat(TaskRunStateCounts.empty().plus(counts)).isSameAs(counts);
    }

    @Test
    void shouldRoundTripThroughMapWithIntegerCounts() {
        // Given
        TaskRunStateCounts counts = TaskRunStateCounts.of(List.of(taskRun("a", State.Type.SUCCESS), taskRun("a", State.Type.FAILED)));
        Map<String, Map<String, Number>> asJson = Map.of("a", Map.of("SUCCESS", 1, "FAILED", 1));

        // When / Then
        assertThat(TaskRunStateCounts.fromMap(asJson)).isEqualTo(counts);
        assertThat(counts.toMap()).isEqualTo(Map.of("a", Map.of("SUCCESS", 1L, "FAILED", 1L)));
    }

    @Test
    void shouldReturnEmptyWhenMapIsNullOrEmpty() {
        assertThat(TaskRunStateCounts.fromMap(null).isEmpty()).isTrue();
        assertThat(TaskRunStateCounts.fromMap(Map.of()).isEmpty()).isTrue();
    }
}
