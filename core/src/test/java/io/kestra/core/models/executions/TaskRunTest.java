package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TaskRunTest {
    @Test
    void onRunningResendNoAttempts() {
        TaskRun taskRun = TaskRun.builder()
            .state(new State())
            .build()
            .onRunningResend();

        assertThat(taskRun.getAttempts().size(), is(1));
        assertThat(taskRun.getAttempts().get(0).getState().getHistories().get(0), is(taskRun.getState().getHistories().get(0)));
        assertThat(taskRun.getAttempts().get(0).getState().getCurrent(), is(State.Type.KILLED));
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

        assertThat(taskRun.getAttempts().size(), is(1));
        assertThat(taskRun.getAttempts().get(0).getState().getHistories().get(0), is(not(taskRun.getState().getHistories().get(0))));
        assertThat(taskRun.getAttempts().get(0).getState().getCurrent(), is(State.Type.KILLED));
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

        assertThat(taskRun.getAttempts().size(), is(2));
        assertThat(taskRun.getAttempts().get(1).getState().getHistories().get(0), is(not(taskRun.getState().getHistories().get(0))));
        assertThat(taskRun.getAttempts().get(1).getState().getCurrent(), is(State.Type.KILLED));
    }

}