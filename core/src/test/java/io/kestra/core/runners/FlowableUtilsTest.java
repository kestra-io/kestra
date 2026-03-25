package io.kestra.core.runners;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.plugin.core.debug.Return;

import static org.assertj.core.api.Assertions.assertThat;

class FlowableUtilsTest {

    @Test
    void resolveSequentialNexts_shouldNotSkipTaskWhenPreviousFlowableProducesMultipleTaskRuns() {
        Execution base = Execution.builder()
            .id("test-execution")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING))
            .build();

        ResolvedTask taskA = resolvedTask("task_a");
        ResolvedTask waitFor = resolvedTask("wait_for");
        ResolvedTask taskB = resolvedTask("task_b");
        ResolvedTask taskC = resolvedTask("task_c");

        TaskRun taskATaskRun = TaskRun.of(base, taskA).withState(State.Type.SUCCESS);
        TaskRun waitForTaskRunIter1 = TaskRun.of(base, waitFor).withState(State.Type.SUCCESS);
        TaskRun waitForTaskRunIter2 = TaskRun.of(base, waitFor).withState(State.Type.SUCCESS);
        TaskRun taskBTaskRun = TaskRun.of(base, taskB).withState(State.Type.SUCCESS);

        Execution execution = base.toBuilder()
            .taskRunList(List.of(taskATaskRun, waitForTaskRunIter1, waitForTaskRunIter2, taskBTaskRun))
            .build();

        // When
        List<NextTaskRun> next = FlowableUtils.resolveSequentialNexts(
            execution,
            List.of(taskA, waitFor, taskB, taskC)
        );

        assertThat(next).hasSize(1);
        assertThat(next.getFirst().getTaskRun().getTaskId()).isEqualTo("task_c");
    }

    private static ResolvedTask resolvedTask(String id) {
        return ResolvedTask.of(
            Return.builder()
                .id(id)
                .type(Return.class.getName())
                .format(Property.ofValue(id))
                .build()
        );
    }
}
