package io.kestra.core.models.executions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTest {

    @Test
    void hasTaskRunJoinableTrue() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(new State(State.Type.RUNNING, new State()))
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(
                        new State(
                            State.Type.FAILED, new State()
                                .withState(State.Type.RUNNING)
                        )
                    )
                    .build()
            )
        ).isTrue();
    }

    @Test
    void hasTaskRunJoinableSameState() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(new State())
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(new State())
                    .build()
            )
        ).isFalse();
    }

    @Test
    void hasTaskRunJoinableFailedExecutionFromExecutor() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(
                            new State(
                                State.Type.FAILED, new State()
                                    .withState(State.Type.RUNNING)
                            )
                        )
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(new State(State.Type.RUNNING, new State()))
                    .build()
            )
        ).isFalse();
    }

    @Test
    void hasTaskRunJoinableRestartFailed() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(
                            new State(
                                State.Type.CREATED, new State()
                                    .withState(State.Type.RUNNING)
                                    .withState(State.Type.FAILED)
                            )
                        )
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(
                        new State(
                            State.Type.FAILED, new State()
                                .withState(State.Type.RUNNING)
                        )
                    )
                    .build()
            )
        ).isFalse();
    }

    @Test
    void hasTaskRunJoinableRestartSuccess() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(
                            new State(
                                State.Type.CREATED, new State()
                                    .withState(State.Type.RUNNING)
                                    .withState(State.Type.SUCCESS)
                            )
                        )
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(
                        new State(
                            State.Type.SUCCESS, new State()
                                .withState(State.Type.RUNNING)
                                .withState(State.Type.SUCCESS)
                        )
                    )
                    .build()
            )
        ).isTrue();
    }

    @Test
    void hasTaskRunJoinableAfterRestart() {
        Execution execution = Execution.builder()
            .taskRunList(
                Collections.singletonList(
                    TaskRun.builder()
                        .id("test")
                        .state(
                            new State(
                                State.Type.CREATED, new State()
                                    .withState(State.Type.RUNNING)
                                    .withState(State.Type.FAILED)
                            )
                        )
                        .build()
                )
            )
            .build();

        assertThat(
            execution.hasTaskRunJoinable(
                TaskRun.builder()
                    .id("test")
                    .state(
                        new State(
                            State.Type.SUCCESS, new State()
                                .withState(State.Type.RUNNING)
                                .withState(State.Type.FAILED)
                                .withState(State.Type.CREATED)
                                .withState(State.Type.RUNNING)
                        )
                    )
                    .build()
            )
        ).isTrue();
    }

    @Test
    void originalId() {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .state(new State())
            .build();
        assertThat(execution.getOriginalId()).isEqualTo(execution.getId());

        Execution restart1 = execution.childExecution(
            IdUtils.create(),
            execution.getTaskRunList(),
            execution.withState(State.Type.RESTARTED).getState()
        );
        assertThat(restart1.getOriginalId()).isEqualTo(execution.getId());

        Execution restart2 = restart1.childExecution(
            IdUtils.create(),
            restart1.getTaskRunList(),
            restart1.withState(State.Type.PAUSED).getState()
        );
        assertThat(restart2.getOriginalId()).isEqualTo(execution.getId());
    }

    @Test
    void labels() {
        final Execution execution = Execution.builder()
            .labels(List.of(new Label("test", "test-value")))
            .build();

        assertThat(execution.getLabels()).containsExactly(new Label("test", "test-value"));
    }

    @Test
    void labelsGetDeduplicated() {
        final List<Label> duplicatedLabels = List.of(
            new Label("test", "value1"),
            new Label("test", "value2")
        );

        final Execution executionWithLabels = Execution.builder()
            .build()
            .withLabels(duplicatedLabels);
        assertThat(executionWithLabels.getLabels()).containsExactly(new Label("test", "value2"));

        final Execution executionBuilder = Execution.builder()
            .labels(duplicatedLabels)
            .build();
        assertThat(executionBuilder.getLabels()).containsExactly(new Label("test", "value2"));
    }

    @Test
    @Disabled("Solve label deduplication on instantization")
    void labelsGetDeduplicatedOnNewInstance() {
        final List<Label> duplicatedLabels = List.of(
            new Label("test", "value1"),
            new Label("test", "value2")
        );

        final Execution executionNew = new Execution(
            "foo",
            "id",
            "namespace",
            "flowId",
            1,
            Collections.emptyList(),
            Map.of(),
            Map.of(),
            duplicatedLabels,
            Map.of(),
            State.of(State.Type.SUCCESS, Collections.emptyList()),
            "parentId",
            "originalId",
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        assertThat(executionNew.getLabels()).containsExactly(new Label("test", "value2"));
    }

    @Test
    void shouldGuessCancelledWhenTaskRunCancelled() {
        // Given a flow whose single task run ended CANCELLED (e.g. workerSelector fallback: CANCEL)
        Flow flow = flowWithTask("will-cancel");
        Execution execution = executionWithTaskRun("will-cancel", State.Type.CANCELLED);

        // When guessing the final state

        // Then the execution must not be silently reported as SUCCESS
        assertThat(execution.guessFinalState(flow)).isEqualTo(State.Type.CANCELLED);
    }

    @Test
    void shouldGuessFailedWhenTaskRunFailedTakesPrecedenceOverCancelled() {
        // Given one FAILED and one CANCELLED task run
        Return failed = Return.builder().id("failed").type(Return.class.getName()).build();
        Return cancelled = Return.builder().id("cancelled").type(Return.class.getName()).build();
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.test")
            .tasks(List.of(failed, cancelled))
            .build();
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .taskRunList(List.of(
                TaskRun.builder().id(IdUtils.create()).taskId("failed").state(new State(State.Type.FAILED, new State())).build(),
                TaskRun.builder().id(IdUtils.create()).taskId("cancelled").state(new State(State.Type.CANCELLED, new State())).build()
            ))
            .build();

        // When guessing the final state

        // Then FAILED takes precedence over CANCELLED
        assertThat(execution.guessFinalState(flow)).isEqualTo(State.Type.FAILED);
    }

    private static Flow flowWithTask(String taskId) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.test")
            .tasks(List.of(Return.builder().id(taskId).type(Return.class.getName()).build()))
            .build();
    }

    private static Execution executionWithTaskRun(String taskId, State.Type state) {
        return Execution.builder()
            .id(IdUtils.create())
            .taskRunList(List.of(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .taskId(taskId)
                    .state(new State(state, new State()))
                    .build()
            ))
            .build();
    }
}
