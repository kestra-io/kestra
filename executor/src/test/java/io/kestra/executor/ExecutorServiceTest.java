package io.kestra.executor;

import io.kestra.core.debug.Breakpoint;
import java.time.Instant;
import java.util.List;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class ExecutorServiceTest {
    @Inject
    private ExecutorService executorService;

    @Test
    void shouldSkipSLAEvaluationWhenExecutionKindIsLoop() throws Exception {
        SLA sla = ExecutionAssertionSLA.builder()
            .id("always-fails")
            .type(SLA.Type.EXECUTION_ASSERTION)
            .behavior(SLA.Behavior.FAIL)
            ._assert("false")
            .build();
        var task = Log.builder().id("task").type(Log.class.getName()).message("hello").build();
        var flow = Flow.builder().tenantId("tenant").namespace("io.kestra.unit-test").id(IdUtils.create()).tasks(List.of(task)).sla(List.of(sla)).build();
        var execution = Execution.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .kind(ExecutionKind.LOOP)
            .state(new State())
            .build();
        var executor = new ExecutorContext(execution, FlowWithSource.of(flow, "flow-source"));

        ExecutorContext result = executorService.handleExecutionChangedSLA(executor);

        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    @Test
    void shouldSkipAfterExecutionTasksWhenExecutionKindIsLoop() throws Exception {
        var innerTask = Log.builder().id("inner").type(Log.class.getName()).message("inner").build();
        var afterTask = Log.builder().id("after").type(Log.class.getName()).message("after").build();
        var loopTask = Loop.builder().id("loop").type(Loop.class.getName()).values("[\"a\"]").tasks(List.of(innerTask)).build();

        var flow = Flow.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unit-test")
            .id(IdUtils.create())
            .tasks(List.of(loopTask))
            .afterExecution(List.of(afterTask))
            .build();

        var loopTaskRun = TaskRun.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(loopTask.getId())
            .state(new State())
            .build();

        var parentExecution = Execution.builder()
            .tenantId("tenant")
            .id(loopTaskRun.getExecutionId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State())
            .build();

        var innerTaskRun = TaskRun.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(innerTask.getId())
            .parentTaskRunId(loopTaskRun.getId())
            .state(new State())
            .build()
            .withState(State.Type.SUCCESS);

        var loopExecution = parentExecution.loopExecution(loopTaskRun, 0, null, "a",false)
            .toBuilder()
            .taskRunList(List.of(innerTaskRun))
            .build();

        var executor = new ExecutorContext(loopExecution, FlowWithSource.of(flow, "flow-source"));

        ExecutorContext result = executorService.process(executor);

        // a Loop sub-execution reuses the parent flow, which may declare afterExecution tasks: they
        // belong to the parent execution's own completion, not to each loop iteration's sub-execution
        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(result.getExecution().getTaskRunList()).extracting(TaskRun::getTaskId).containsExactly("inner");
    }

    @Test
    void shouldCancelExecutionWhenSLAViolatedWithCancelBehavior() throws Exception {
        ExecutorContext result = executorService.handleExecutionChangedSLA(slaExecutor(SLA.Behavior.CANCEL, "false", null));

        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
    }

    @Test
    void shouldFailExecutionWhenSLAViolatedWithFailBehavior() throws Exception {
        ExecutorContext result = executorService.handleExecutionChangedSLA(slaExecutor(SLA.Behavior.FAIL, "false", null));

        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldAddLabelsWhenSLAViolatedWithNoneBehavior() throws Exception {
        ExecutorContext result = executorService.handleExecutionChangedSLA(slaExecutor(SLA.Behavior.NONE, "false", List.of(new Label("sla", "violated"))));

        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(result.getExecution().getLabels()).contains(new Label("sla", "violated"));
    }

    @Test
    void shouldNotChangeExecutionWhenSLASatisfied() throws Exception {
        ExecutorContext executor = slaExecutor(SLA.Behavior.CANCEL, "true", null);

        ExecutorContext result = executorService.handleExecutionChangedSLA(executor);

        assertThat(result).isSameAs(executor);
        assertThat(result.getExecution().getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    private ExecutorContext slaExecutor(SLA.Behavior behavior, String assertExpr, List<Label> labels) {
        SLA sla = ExecutionAssertionSLA.builder()
            .id("sla")
            .type(SLA.Type.EXECUTION_ASSERTION)
            .behavior(behavior)
            .labels(labels)
            ._assert(assertExpr)
            .build();
        var task = Log.builder().id("task").type(Log.class.getName()).message("hello").build();
        var flow = Flow.builder().tenantId("tenant").namespace("io.kestra.unit-test").id(IdUtils.create()).tasks(List.of(task)).sla(List.of(sla)).build();
        var execution = Execution.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State())
            .build();

        return new ExecutorContext(execution, FlowWithSource.of(flow, "flow-source"));
    }

    @Test
    void shouldNotReSuspendTaskJustResumedFromBreakpoint() throws Exception {
        var hello = Log.builder().id("hello").type(Log.class.getName()).message("hello").build();
        var flow = Flow.builder().tenantId("tenant").namespace("io.kestra.unit-test").id(IdUtils.create()).tasks(List.of(hello)).build();

        State resumedState = new State(
            State.Type.CREATED, List.of(
                new State.History(State.Type.CREATED, Instant.now().minusSeconds(2)),
                new State.History(State.Type.BREAKPOINT, Instant.now().minusSeconds(1)),
                new State.History(State.Type.CREATED, Instant.now())
            )
        );

        var taskRun = TaskRun.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(hello.getId())
            .state(resumedState)
            .build();

        var execution = Execution.builder()
            .tenantId("tenant")
            .id(taskRun.getExecutionId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State(State.Type.RUNNING, List.of(new State.History(State.Type.RUNNING, Instant.now()))))
            .taskRunList(List.of(taskRun))
            .breakpoints(List.of(new Breakpoint("hello", null)))
            .build();

        var executor = new ExecutorContext(execution, FlowWithSource.of(flow, "flow-source"));

        ExecutorContext result = executorService.process(executor);

        AssertionsForClassTypes.assertThat(result.getExecution().getState().getCurrent()).isNotEqualTo(State.Type.BREAKPOINT);
    }
}
