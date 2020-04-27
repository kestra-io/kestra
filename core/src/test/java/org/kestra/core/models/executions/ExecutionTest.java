package org.kestra.core.models.executions;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.flows.State;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ExecutionTest {
    private static final TaskRun.TaskRunBuilder TASK_RUN = TaskRun.builder()
        .id("test");

    @Test
    void hasTaskRunJoinableTrue() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State(State.Type.RUNNING, new State()))
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State(State.Type.FAILED, new State()
                .withState(State.Type.RUNNING)
            ))
            .build()
        ), is(true));
    }

    @Test
    void hasTaskRunJoinableSameState() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State())
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State())
            .build()
        ), is(false));
    }

    @Test
    void hasTaskRunJoinableFailedExecutionFromExecutor() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State(State.Type.FAILED, new State()
                    .withState(State.Type.RUNNING)
                ))
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State(State.Type.RUNNING, new State()))
            .build()
        ), is(false));
    }

    @Test
    void hasTaskRunJoinableRestartFailed() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State(State.Type.CREATED, new State()
                    .withState(State.Type.RUNNING)
                    .withState(State.Type.FAILED)
                ))
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State(State.Type.FAILED, new State()
                .withState(State.Type.RUNNING)
            ))
            .build()
        ), is(false));
    }

    @Test
    void hasTaskRunJoinableRestartSuccess() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State(State.Type.CREATED, new State()
                    .withState(State.Type.RUNNING)
                    .withState(State.Type.SUCCESS)
                ))
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State(State.Type.SUCCESS, new State()
                .withState(State.Type.RUNNING)
                .withState(State.Type.SUCCESS)
            ))
            .build()
        ), is(true));
    }

    @Test
    void hasTaskRunJoinableAfterRestart() {
        Execution execution = Execution.builder()
            .taskRunList(Collections.singletonList(TASK_RUN
                .state(new State(State.Type.CREATED, new State()
                    .withState(State.Type.RUNNING)
                    .withState(State.Type.FAILED)
                ))
                .build())
            )
            .build();

        assertThat(execution.hasTaskRunJoinable(TASK_RUN
            .state(new State(State.Type.SUCCESS, new State()
                .withState(State.Type.RUNNING)
                .withState(State.Type.FAILED)
                .withState(State.Type.CREATED)
                .withState(State.Type.RUNNING)
            ))
            .build()
        ), is(true));
    }

    @Test
    void isJustRestartedExecution() {
        Execution execution = Execution.builder()
            .state(new State()
                .withState(State.Type.RUNNING)
                .withState(State.Type.FAILED)
                .withState(State.Type.RESTARTED)
                .withState(State.Type.RUNNING)
            )
            .build();

        assertThat(execution.isJustRestarted(), is(true));
    }

    @Test
    void isJustRestartedFailed() {
        Execution execution = Execution.builder()
            .state(new State()
                .withState(State.Type.RUNNING)
                .withState(State.Type.FAILED)
                .withState(State.Type.RESTARTED)
                .withState(State.Type.RUNNING)
                .withState(State.Type.FAILED)

            )
            .build();

        assertThat(execution.isJustRestarted(), is(false));
    }

}
