package io.kestra.core.executions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openjdk.jmh.annotations.*;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

/**
 * Compares the termination check that built an {@code IdUtils.fromParts} uid string for every task run and every
 * resolved task with the one keyed on the task id and value, on an execution of 50 terminated task runs.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                  uid strings           task id and value key
 * isTerminated     4636 ns, 25504 B/op   1594 ns, 10568 B/op
 * </pre>
 *
 * @see <a href="https://github.com/kestra-io/kestra/pull/22484">KESTRA#22484</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@org.openjdk.jmh.annotations.State(Scope.Benchmark)
public class ExecutionTerminationBenchmark {
    private static final int TASKS = 50;

    private Execution execution;
    private List<ResolvedTask> resolvedTasks;

    @Setup
    public void setup() {
        List<Task> tasks = new ArrayList<>(TASKS);
        List<TaskRun> taskRuns = new ArrayList<>(TASKS);
        String executionId = IdUtils.create();
        for (int i = 0; i < TASKS; i++) {
            Log task = Log.builder().id("task-" + i).type(Log.class.getName()).message("hello").build();
            tasks.add(task);
            taskRuns.add(TaskRun.builder()
                .id(IdUtils.create())
                .executionId(executionId)
                .namespace("io.kestra.benchmark")
                .flowId("termination")
                .taskId(task.getId())
                .state(new State(State.Type.SUCCESS))
                .build());
        }
        resolvedTasks = ResolvedTask.of(tasks);
        execution = Execution.builder()
            .id(executionId)
            .namespace("io.kestra.benchmark")
            .flowId("termination")
            .state(new State(State.Type.RUNNING))
            .taskRunList(taskRuns)
            .build();
    }

    @Benchmark
    public boolean oldIsTerminated() {
        Set<String> terminatedTaskUids = oldFindTaskRunByTasks(execution.getTaskRunList(), resolvedTasks, null)
            .stream()
            .filter(taskRun -> taskRun.getState().isTerminated())
            .map(taskRun -> oldFromParts(taskRun.getTaskId(), taskRun.getValue()))
            .collect(Collectors.toSet());

        return resolvedTasks.stream().allMatch(resolvedTask -> terminatedTaskUids.contains(oldFromParts(resolvedTask.getTask().getId(), resolvedTask.getValue())));
    }

    @Benchmark
    public boolean newIsTerminated() {
        return execution.isTerminated(resolvedTasks);
    }

    private static List<TaskRun> oldFindTaskRunByTasks(List<TaskRun> taskRunList, List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        if (resolvedTasks == null || taskRunList == null) {
            return List.of();
        }

        Map<String, ResolvedTask> resolvedTaskMap = HashMap.newHashMap(resolvedTasks.size());
        resolvedTasks.forEach(resolvedTask -> resolvedTaskMap.put(oldFromParts(resolvedTask.getTask().getId(), resolvedTask.getValue()), resolvedTask));
        return taskRunList
            .stream()
            .filter(
                t -> resolvedTaskMap.containsKey(oldFromParts(t.getTaskId(), t.getValue()))
                    && (parentTaskRun == null || parentTaskRun.getId().equals(t.getParentTaskRunId()))
            )
            .toList();
    }

    private static String oldFromParts(String... parts) {
        StringJoiner sj = new StringJoiner(String.valueOf('_'));
        for (String str : parts) {
            if (str != null) {
                sj.add(str);
            }
        }
        return sj.toString();
    }
}
