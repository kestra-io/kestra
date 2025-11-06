package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
            .attempts(Collections.singletonList(TaskRunAttempt.builder()
                .state(new State().withState(State.Type.RUNNING))
                .build()
            ))
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
            .attempts(Collections.singletonList(TaskRunAttempt.builder()
                .state(new State().withState(State.Type.SUCCESS))
                .build()
            ))
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size()).isEqualTo(2);
        assertThat(taskRun.getAttempts().get(1).getState().getHistories().getFirst()).isNotEqualTo(taskRun.getState().getHistories().getFirst());
        assertThat(taskRun.getAttempts().get(1).getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

}