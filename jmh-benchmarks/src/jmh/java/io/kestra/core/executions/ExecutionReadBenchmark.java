package io.kestra.core.executions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;

/**
 * Compares reading an execution the way the PostgreSQL repository did, from a {@code String} the driver decoded from
 * the UTF-8 bytes of the {@code jsonb} column, with parsing those bytes directly.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 * task runs        from a decoded String    from bytes
 * 5                13.1 us, 20.3 KB/op      11.1 us, 15.4 KB/op
 * 30               69.4 us, 104.6 KB/op     56.0 us, 77.9 KB/op
 * 100              225.8 us, 341.0 KB/op    184.0 us, 253.0 KB/op
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Benchmark)
public class ExecutionReadBenchmark {
    @Param({ "5", "30", "100" })
    private int taskRuns;

    private ObjectMapper mapper;
    private byte[] json;

    @Setup
    public void setup() throws IOException {
        mapper = JacksonMapper.ofJson();
        String executionId = IdUtils.create();
        List<TaskRun> taskRunList = new ArrayList<>(taskRuns);
        for (int i = 0; i < taskRuns; i++) {
            State state = new State(State.Type.CREATED).withState(State.Type.RUNNING).withState(State.Type.SUCCESS);
            taskRunList.add(TaskRun.builder()
                .tenantId("main")
                .id(IdUtils.create())
                .executionId(executionId)
                .namespace("io.kestra.benchmark")
                .flowId("execution-read")
                .taskId("task-" + i)
                .attempts(List.of(TaskRunAttempt.builder().state(state).workerId(IdUtils.create()).build()))
                .state(state)
                .build());
        }
        Execution execution = Execution.builder()
            .tenantId("main")
            .id(executionId)
            .namespace("io.kestra.benchmark")
            .flowId("execution-read")
            .flowRevision(1)
            .labels(List.of(new Label("system.correlationId", executionId), new Label("env", "prod")))
            .state(new State(State.Type.CREATED).withState(State.Type.RUNNING))
            .taskRunList(taskRunList)
            .build();
        json = mapper.writeValueAsBytes(execution);
    }

    @Benchmark
    public Execution fromDecodedString() throws IOException {
        return mapper.readValue(new String(json, StandardCharsets.UTF_8), Execution.class);
    }

    @Benchmark
    public Execution fromBytes() throws IOException {
        return mapper.readValue(json, Execution.class);
    }
}
