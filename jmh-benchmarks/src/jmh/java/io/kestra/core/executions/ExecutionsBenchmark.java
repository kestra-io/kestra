package io.kestra.core.executions;

import java.util.ArrayList;

import com.google.common.collect.Streams;
import io.kestra.core.models.flows.State;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.utils.IdUtils;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Thread)
public class ExecutionsBenchmark {
    List<TaskRun> taskRuns;

    @Setup(Level.Invocation)
    public void setup(){
        taskRuns = new ArrayList<>();
        State state = new State(State.Type.CREATED);
        for(int i = 0; i < 100; i++){
            taskRuns.add(taskRunSetUp(state));
        }
    }
    /**
     * @see <a href="https://github.com/kestra-io/kestra/pull/14385">KESTRA#14385</a>
     */
    @Benchmark
    public Optional<TaskRun> oldFindLastCreatedTaskRun(){
        return Streams.findLast(
             taskRuns
            .stream()
            .filter(t -> t.getState().isCreated())
        );
    }
    @Benchmark
    public Optional<TaskRun> newFindLastCreatedTaskRun(){
         return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isCreated())
            .findFirst();
    }
     TaskRun taskRunSetUp(State state){
       return TaskRun.builder()
            .tenantId(IdUtils.create())
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace(IdUtils.create())
            .flowId(IdUtils.create())
            .taskId(IdUtils.create())
            .parentTaskRunId(IdUtils.create())
            .value(IdUtils.create())
            .iteration(1)
            .state(state)
            .build();
    }
}
