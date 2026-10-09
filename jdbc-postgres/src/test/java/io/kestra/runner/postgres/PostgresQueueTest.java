package io.kestra.runner.postgres;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.runner.JdbcQueueTest;

import static org.assertj.core.api.Assertions.assertThatNoException;

class PostgresQueueTest extends JdbcQueueTest {
    @Test
    void invalidWorkerTaskWithNullCharShouldBeSanitized() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .taskId("taskId")
                    .id(IdUtils.create())
                    .executionId(IdUtils.create())
                    .namespace("namespace")
                    .flowId("flowId")
                    .state(new State().withState(State.Type.SUCCESS))
                    .build()
            )
            .outputs(Map.of("value", "\u0000"))
            .build();

        // JdbcJsonbUtils strips null bytes and their JSON-escaped form before storage,
        // so the emit must succeed rather than throw.
        assertThatNoException().isThrownBy(() -> workerTaskResultQueue.emit(workerTaskResult));
    }

    @Test
    void invalidWorkerTaskWithLoneHighSurrogateShouldBeSanitized() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .taskId("taskId")
                    .id(IdUtils.create())
                    .executionId(IdUtils.create())
                    .namespace("namespace")
                    .flowId("flowId")
                    .state(new State().withState(State.Type.SUCCESS))
                    .build()
            )
            .outputs(Map.of("value", "test\uD800text"))
            .build();

        // JdbcJsonbUtils replaces lone surrogates by U+FFFD before storage, so the emit must succeed rather than throw.
        assertThatNoException().isThrownBy(() -> workerTaskResultQueue.emit(workerTaskResult));
    }

    @Test
    void invalidWorkerTaskWithLoneLowSurrogateShouldBeSanitized() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .taskId("taskId")
                    .id(IdUtils.create())
                    .executionId(IdUtils.create())
                    .namespace("namespace")
                    .flowId("flowId")
                    .state(new State().withState(State.Type.SUCCESS))
                    .build()
            )
            .outputs(Map.of("value", "\uDC59 test"))
            .build();

        // JdbcJsonbUtils replaces lone surrogates by U+FFFD before storage, so the emit must succeed rather than throw.
        assertThatNoException().isThrownBy(() -> workerTaskResultQueue.emit(workerTaskResult));
    }
}