package io.kestra.core.models.executions;

import io.kestra.core.models.Label;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.flows.State;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    void originalId() {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .state(new State())
            .build();
        assertThat(execution.getOriginalId(), is(execution.getId()));

        Execution restart1 = execution.childExecution(
            IdUtils.create(),
            execution.getTaskRunList(),
            execution.withState(State.Type.RESTARTED).getState()
        );
        assertThat(restart1.getOriginalId(), is(execution.getId()));

        Execution restart2 = restart1.childExecution(
            IdUtils.create(),
            restart1.getTaskRunList(),
            restart1.withState(State.Type.PAUSED).getState()
        );
        assertThat(restart2.getOriginalId(), is(execution.getId()));
    }

    @Test
    void labels() {
        final Execution execution = Execution.builder()
            .labels(List.of(new Label("test", "test-value")))
            .build();

        assertThat(execution.getLabels().size(), is(1));
        assertThat(execution.getLabels().getFirst(), is(new Label("test", "test-value")));
    }
}
