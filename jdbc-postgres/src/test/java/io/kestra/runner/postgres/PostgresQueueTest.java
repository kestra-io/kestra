package io.kestra.runner.postgres;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.runner.JdbcQueueTest;
import org.jooq.exception.DataException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresQueueTest extends JdbcQueueTest {
    @Test
    void invalidWorkerTaskShouldThrowDataException() throws QueueException {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(TaskRun.builder()
                .taskId("taskId")
                .id(IdUtils.create())
                .namespace("namespace")
                .flowId("flowId")
                .state(new State().withState(State.Type.SUCCESS))
                .outputs(Variables.inMemory(Map.of("value", "\u0000")))
                .build()
            )
            .build();

        var exception = assertThrows(QueueException.class, () -> workerTaskResultQueue.emit(workerTaskResult));
        assertThat(exception).isInstanceOf(UnsupportedMessageException.class);
        assertThat(exception.getMessage()).contains("ERROR: unsupported Unicode escape sequence");
        assertThat(exception.getCause()).isInstanceOf(DataException.class);
    }
}