package io.kestra.runner.postgres;

import java.util.Map;

import org.jooq.exception.DataException;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.runner.JdbcQueueTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresQueueTest extends JdbcQueueTest {
    @Test
    void invalidWorkerTaskWithNullCharShouldBeSanitized() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .taskId("taskId")
                    .id(IdUtils.create())
                    .namespace("namespace")
                    .flowId("flowId")
                    .state(new State().withState(State.Type.SUCCESS))
                    .outputs(Variables.inMemory(Map.of("value", "\u0000")))
                    .build()
            )
            .build();

        // JdbcJsonbUtils strips null bytes and their JSON-escaped form before storage,
        // so the emit must succeed rather than throw.
        assertThatNoException().isThrownBy(() -> workerTaskResultQueue.emit(workerTaskResult));
    }
}