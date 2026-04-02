package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.core.debug.Return;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Loop} task's pure in-memory logic, and for
 * the {@link Execution#loopExecution(String, TaskRun, String, int)} method that creates
 * loop sub-executions.
 *
 * <p><strong>Context:</strong> The {@code Loop} task was introduced to run child tasks for each
 * value in a list via transparent sub-executions. The {@link LoopCaseTest} integration helper
 * verifies end-to-end behavior (state transitions, sub-execution counts via repository queries,
 * task output persistence) and requires a running Kestra runner and database. This test class
 * covers the pure in-memory logic that does not need that infrastructure.
 *
 * <p><strong>What these unit tests cover:</strong>
 * <ul>
 *   <li>{@code Loop.isMySubExecution} — four boolean branches (execution kind mismatch,
 *       null loopRun, taskRunId mismatch, happy path)</li>
 *   <li>{@code Loop.allChildTasks} — list combination across tasks / errors / finally,
 *       including null handling</li>
 *   <li>{@code Execution.loopExecution} — LoopRun construction and parent-chain accumulation
 *       for nested loops</li>
 * </ul>
 *
 * <p><strong>What stays in the integration tests:</strong> Execution state transitions
 * (SUCCESS/FAILED), actual sub-execution creation and retrieval from the repository, task output
 * persistence, and concurrency-limit scheduling behavior all require the full Kestra runner
 * and database and remain in {@link LoopCaseTest}.
 */
class LoopTest {

    // ---- Loop.isMySubExecution ----------------------------------------------------------

    @Test
    void isMySubExecution_returnsTrueWhenKindIsLoopAndTaskRunIdMatches() {
        // Given
        TaskRun taskRun = TaskRun.builder().id("tr-1").state(new State()).build();
        LoopRun loopRun = new LoopRun("exec-parent", "loopTask", "tr-1", "value", 0, null);
        Execution execution = Execution.builder()
            .id("exec-1")
            .kind(ExecutionKind.LOOP)
            .loopRun(loopRun)
            .state(new State())
            .build();
        Loop loop = loopWithTasks();

        // When / Then
        assertThat(loop.isMySubExecution(execution, taskRun)).isTrue();
    }

    @Test
    void isMySubExecution_returnsFalseWhenKindIsNotLoop() {
        // Given
        TaskRun taskRun = TaskRun.builder().id("tr-1").state(new State()).build();
        LoopRun loopRun = new LoopRun("exec-parent", "loopTask", "tr-1", "value", 0, null);
        Execution execution = Execution.builder()
            .id("exec-1")
            .loopRun(loopRun) // kind is null, not LOOP
            .state(new State())
            .build();
        Loop loop = loopWithTasks();

        // When / Then
        assertThat(loop.isMySubExecution(execution, taskRun)).isFalse();
    }

    @Test
    void isMySubExecution_returnsFalseWhenLoopRunIsNull() {
        // Given
        TaskRun taskRun = TaskRun.builder().id("tr-1").state(new State()).build();
        Execution execution = Execution.builder()
            .id("exec-1")
            .kind(ExecutionKind.LOOP)
            .state(new State())
            .build(); // loopRun is null
        Loop loop = loopWithTasks();

        // When / Then
        assertThat(loop.isMySubExecution(execution, taskRun)).isFalse();
    }

    @Test
    void isMySubExecution_returnsFalseWhenTaskRunIdDoesNotMatch() {
        // Given
        TaskRun taskRun = TaskRun.builder().id("different-id").state(new State()).build();
        LoopRun loopRun = new LoopRun("exec-parent", "loopTask", "tr-1", "value", 0, null);
        Execution execution = Execution.builder()
            .id("exec-1")
            .kind(ExecutionKind.LOOP)
            .loopRun(loopRun)
            .state(new State())
            .build();
        Loop loop = loopWithTasks();

        // When / Then
        assertThat(loop.isMySubExecution(execution, taskRun)).isFalse();
    }

    // ---- Loop.allChildTasks -------------------------------------------------------------

    @Test
    void allChildTasks_returnsOnlyTasksWhenErrorsAndFinallyAreNull() {
        // Given
        Task task = childTask("t1");
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("a"))
            .tasks(List.of(task))
            .build(); // errors and finally default to null

        // When
        List<Task> result = loop.allChildTasks();

        // Then
        assertThat(result).containsExactly(task);
    }

    @Test
    void allChildTasks_combinesTasksErrorsAndFinally() {
        // Given
        Task task = childTask("t1");
        Task errorTask = childTask("e1");
        Task finallyTask = childTask("f1");
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("a"))
            .tasks(List.of(task))
            .errors(List.of(errorTask))
            ._finally(List.of(finallyTask))
            .build();

        // When
        List<Task> result = loop.allChildTasks();

        // Then
        assertThat(result).containsExactly(task, errorTask, finallyTask);
    }

    // ---- Execution.loopExecution --------------------------------------------------------

    @Test
    void loopExecution_setsKindParentIdAndLoopRunForTopLevelLoop() {
        // Given
        TaskRun taskRun = TaskRun.builder().id("tr-1").taskId("loopTask").state(new State()).build();
        Execution parent = Execution.builder()
            .id("exec-parent")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .build();

        // When
        Execution loopExec = parent.loopExecution("loop-exec-1", taskRun, "value1", 0);

        // Then
        assertThat(loopExec.getKind()).isEqualTo(ExecutionKind.LOOP);
        assertThat(loopExec.getParentId()).isEqualTo("exec-parent");
        LoopRun lr = loopExec.getLoopRun();
        assertThat(lr).isNotNull();
        assertThat(lr.executionId()).isEqualTo("exec-parent");
        assertThat(lr.taskId()).isEqualTo("loopTask");
        assertThat(lr.taskRunId()).isEqualTo("tr-1");
        assertThat(lr.value()).isEqualTo("value1");
        assertThat(lr.index()).isEqualTo(0);
        assertThat(lr.parents()).isNull(); // top-level loop has no parent context
    }

    @Test
    void loopExecution_setsParentChainWhenCreatingNestedLoopExecution() {
        // Given: a loop execution (loop1) with its own LoopRun
        LoopRun loop1LoopRun = new LoopRun("exec-0", "loop1", "tr1", "parentValue", 1, null);
        Execution loop1Exec = Execution.builder()
            .id("loop1-exec")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .kind(ExecutionKind.LOOP)
            .loopRun(loop1LoopRun)
            .build();
        TaskRun loop2TaskRun = TaskRun.builder().id("tr2").taskId("loop2").state(new State()).build();

        // When: creating a nested loop execution from loop1
        Execution loop2Exec = loop1Exec.loopExecution("loop2-exec-1", loop2TaskRun, "childValue", 0);

        // Then: the parent chain holds loop1's loopRun info
        LoopRun lr = loop2Exec.getLoopRun();
        assertThat(lr.value()).isEqualTo("childValue");
        assertThat(lr.parents()).isNotNull().hasSize(1);
        assertThat(lr.parents().getFirst().value()).isEqualTo("parentValue");
        assertThat(lr.parents().getFirst().index()).isEqualTo(1);
    }

    @Test
    void loopExecution_accumulatesParentsAcrossThreeLevelsOfNesting() {
        // Given: a doubly-nested loop execution carrying an existing grandparent
        LoopRun.Parent grandparent = new LoopRun.Parent("gp-value", 0);
        LoopRun loop2LoopRun = new LoopRun("exec-0", "loop2", "tr2", "p-value", 2, List.of(grandparent));
        Execution loop2Exec = Execution.builder()
            .id("loop2-exec")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .kind(ExecutionKind.LOOP)
            .loopRun(loop2LoopRun)
            .build();
        TaskRun loop3TaskRun = TaskRun.builder().id("tr3").taskId("loop3").state(new State()).build();

        // When
        Execution loop3Exec = loop2Exec.loopExecution("loop3-exec-1", loop3TaskRun, "leaf-value", 3);

        // Then: parents = [grandparent, loop2], in order from outermost to innermost
        LoopRun lr = loop3Exec.getLoopRun();
        assertThat(lr.value()).isEqualTo("leaf-value");
        assertThat(lr.parents()).hasSize(2);
        assertThat(lr.parents().get(0).value()).isEqualTo("gp-value");
        assertThat(lr.parents().get(0).index()).isEqualTo(0);
        assertThat(lr.parents().get(1).value()).isEqualTo("p-value");
        assertThat(lr.parents().get(1).index()).isEqualTo(2);
    }

    // ---- helpers -----------------------------------------------------------------------

    private static Loop loopWithTasks() {
        return Loop.builder()
            .id("loopTask")
            .type(Loop.class.getName())
            .values(List.of("value"))
            .tasks(List.of(childTask("t1")))
            .build();
    }

    private static Task childTask(String id) {
        return Return.builder()
            .id(id)
            .type(Return.class.getName())
            .format(Property.ofValue(id))
            .build();
    }
}
