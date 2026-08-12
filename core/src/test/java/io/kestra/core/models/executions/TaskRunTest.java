package io.kestra.core.models.executions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunTest {
    @Test
    void onRunningResendNoAttempts() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(1);
        assertThat(taskRun.getAttempts().getFirst().getState().getHistories().getFirst()).isEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void onRunningResendRunning() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .attempts(
                Collections.singletonList(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.RUNNING))
                        .build()
                )
            )
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(1);
        assertThat(taskRun.getAttempts().getFirst().getState().getHistories().getFirst()).isNotEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void onRunningResendTerminated() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .attempts(
                Collections.singletonList(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.SUCCESS))
                        .build()
                )
            )
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(2);
        assertThat(taskRun.getAttempts().get(1).getState().getHistories().getFirst()).isNotEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().get(1).getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void valueForVariablesShouldReturnNullWhenValueIsNull() {
        TaskRun taskRun = TaskRun.builder().state(new State()).build();

        assertThat(taskRun.valueForVariables()).isNull();
    }

    @Test
    void valueForVariablesShouldKeepScalarIterationValue() {
        TaskRun taskRun = TaskRun.builder().state(new State()).value("item-1").build();

        assertThat(taskRun.valueForVariables()).isEqualTo("item-1");
    }

    @Test
    void valueForVariablesShouldParseJsonObjectFromLoopRow() {
        String rowJson = "{\"id\":1,\"title\":\"repro\",\"is_completed\":false}";
        TaskRun taskRun = TaskRun.builder().state(new State()).value(rowJson).build();

        assertThat(taskRun.valueForVariables()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) taskRun.valueForVariables()).get("id")).isEqualTo(1);
        assertThat(((Map<?, ?>) taskRun.valueForVariables()).get("title")).isEqualTo("repro");
        assertThat(((Map<?, ?>) taskRun.valueForVariables()).get("is_completed")).isEqualTo(false);
    }

    @Test
    void valueForVariablesShouldParseJsonArray() {
        TaskRun taskRun = TaskRun.builder().state(new State()).value("[1,2,3]").build();

        assertThat(taskRun.valueForVariables()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void valueForVariablesShouldFallbackToRawStringOnInvalidJson() {
        TaskRun taskRun = TaskRun.builder().state(new State()).value("{not-json").build();

        assertThat(taskRun.valueForVariables()).isEqualTo("{not-json");
    }

}