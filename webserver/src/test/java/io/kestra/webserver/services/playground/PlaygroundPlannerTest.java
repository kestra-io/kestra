package io.kestra.webserver.services.playground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PlaygroundPlannerTest {
    private static final String FLOW = """
        id: playground
        namespace: io.kestra.tests
        variables:
          greeting: %s
        tasks:
          - id: a
            type: io.kestra.plugin.core.debug.Return
            format: "%s"
          - id: b
            type: io.kestra.plugin.core.debug.Return
            format: b
          - id: c
            type: io.kestra.plugin.core.debug.Return
            format: c
        finally:
          - id: fin
            type: io.kestra.plugin.core.debug.Return
            format: fin
        afterExecution:
          - id: after
            type: io.kestra.plugin.core.debug.Return
            format: after
        """;

    @Test
    void shouldReuseTheTasksBeforeTheTargetWhenNothingChanged() {
        Flow flow = flow("hello", "a");
        Execution previous = ranUpTo("c", "a", "b");

        PlaygroundPlan plan = PlaygroundPlanner.plan(flow, "c", PlaygroundRunMode.UP_TO, previous, flow);

        assertThat(plan.freshReason()).isEqualTo(PlaygroundFreshReason.NONE);
        assertThat(plan.buildOnExecutionId()).isEqualTo(previous.getId());
        assertThat(plan.replayFromTaskId()).isEqualTo("c");
        assertThat(plan.replayFromTaskRunId()).isEqualTo(previous.findTaskRunsByTaskId("c").getFirst().getId());
        assertThat(plan.reusedTaskIds()).containsExactly("a", "b");
        assertThat(plan.breakpoints()).containsExactly("fin");
    }

    @Test
    void shouldReplayFromTheFirstChangedTaskWhenAnEarlierTaskChanged() {
        Execution previous = ranUpTo("b", "a");

        PlaygroundPlan plan = PlaygroundPlanner.plan(flow("hello", "edited"), "b", PlaygroundRunMode.FROM, previous, flow("hello", "a"));

        assertThat(plan.replayFromTaskId()).isEqualTo("a");
        assertThat(plan.reusedTaskIds()).isEmpty();
        assertThat(plan.breakpoints()).isEmpty();
    }

    @Test
    void shouldStartFreshWhenTheVariablesChanged() {
        PlaygroundPlan plan = PlaygroundPlanner.plan(flow("hi", "a"), "b", PlaygroundRunMode.UP_TO, ranUpTo("b", "a"), flow("hello", "a"));

        assertThat(plan.freshReason()).isEqualTo(PlaygroundFreshReason.VARIABLES_CHANGED);
        assertThat(plan.buildOnExecutionId()).isNull();
        assertThat(plan.breakpoints()).containsExactly("c");
    }

    @Test
    void shouldStartFreshWhenTheReplayPointNeverRan() {
        PlaygroundPlan plan = PlaygroundPlanner.plan(flow("hello", "a"), "c", PlaygroundRunMode.UP_TO, ranUpTo("b", "a"), flow("hello", "a"));

        assertThat(plan.freshReason()).isEqualTo(PlaygroundFreshReason.NOT_REACHED);
    }

    @Test
    void shouldStartFreshWithoutARunToBuildOn() {
        PlaygroundPlan plan = PlaygroundPlanner.plan(flow("hello", "a"), "a", PlaygroundRunMode.UP_TO, null, null);

        assertThat(plan.freshReason()).isEqualTo(PlaygroundFreshReason.NO_PREVIOUS_RUN);
        assertThat(plan.breakpoints()).containsExactly("b");
    }

    @Test
    void shouldStopBeforeFinallyButNeverBeforeAfterExecutionWhenRunningUpToTheLastTasks() {
        Flow flow = flow("hello", "a");

        assertThat(PlaygroundPlanner.nextTaskIds(flow, "c")).containsExactly("fin");
        assertThat(PlaygroundPlanner.nextTaskIds(flow, "fin")).isEmpty();
    }

    private static Flow flow(String greeting, String format) {
        return YamlParser.parse(FLOW.formatted(greeting, format), Flow.class);
    }

    /** A run that stopped before {@code stoppedBefore}, which holds a BREAKPOINT task run, after running {@code ran}. */
    private static Execution ranUpTo(String stoppedBefore, String... ran) {
        String executionId = IdUtils.create();
        List<TaskRun> taskRuns = new ArrayList<>(Arrays.stream(ran).map(id -> taskRun(executionId, id, State.Type.SUCCESS)).toList());
        taskRuns.add(taskRun(executionId, stoppedBefore, State.Type.BREAKPOINT));
        return execution(executionId, taskRuns);
    }

    private static Execution execution(String id, List<TaskRun> taskRuns) {
        return Execution.builder()
            .id(id)
            .namespace("io.kestra.tests")
            .flowId("playground")
            .flowRevision(1)
            .kind(ExecutionKind.PLAYGROUND)
            .taskRunList(taskRuns)
            .state(new State(State.Type.KILLED))
            .build();
    }

    private static TaskRun taskRun(String executionId, String taskId, State.Type state) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .executionId(executionId)
            .namespace("io.kestra.tests")
            .flowId("playground")
            .taskId(taskId)
            .state(new State(state))
            .build();
    }
}
