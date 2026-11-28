package io.kestra.core.queues;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;

/**
 * Compares handing a JDBC queue message to its consumer the way the queue client did, as bytes re-encoded from the
 * {@code String} the driver decoded, with handing over the bytes the database sent; both then deserialize the message.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                    re-encoded from a String    bytes as sent
 * executionEvent     359 ns, 1584 B/op           354 ns, 1160 B/op
 * workerTaskResult   2483 ns, 5904 B/op          2218 ns, 3960 B/op
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Benchmark)
public class QueueMessageReadBenchmark {
    @Param({ "executionEvent", "workerTaskResult" })
    private String message;

    private ObjectMapper mapper;
    private Class<?> type;
    private byte[] payload;

    @Setup
    public void setup() throws IOException {
        mapper = JacksonMapper.ofJson();
        String executionId = IdUtils.create();
        if ("executionEvent".equals(message)) {
            Execution execution = Execution.builder()
                .tenantId("main")
                .id(executionId)
                .namespace("io.kestra.benchmark")
                .flowId("queue-read")
                .state(new State(State.Type.RUNNING))
                .build();
            type = ExecutionEvent.class;
            payload = mapper.writeValueAsBytes(new ExecutionEvent(execution, ExecutionEventType.UPDATED));
        } else {
            State state = new State(State.Type.CREATED).withState(State.Type.RUNNING).withState(State.Type.SUCCESS);
            TaskRun taskRun = TaskRun.builder()
                .tenantId("main")
                .id(IdUtils.create())
                .executionId(executionId)
                .namespace("io.kestra.benchmark")
                .flowId("queue-read")
                .taskId("task")
                .attempts(List.of(TaskRunAttempt.builder().state(state).workerId(IdUtils.create()).build()))
                .state(state)
                .build();
            type = WorkerTaskResult.class;
            payload = mapper.writeValueAsBytes(new WorkerTaskResult(taskRun, Map.of("value", "some output", "count", 42)));
        }
    }

    @Benchmark
    public Object reencodedFromString() throws IOException {
        byte[] bytes = new String(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        return mapper.readValue(bytes, type);
    }

    @Benchmark
    public Object bytesAsSent() throws IOException {
        return mapper.readValue(payload, type);
    }
}
