package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.CyclicGraphException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.GraphTask;
import io.kestra.core.models.hierarchies.Relation;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.YamlParser;
import io.kestra.plugin.core.log.Log;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphUtilsTest {
    private static final int CHAIN_LONGER_THAN_A_RECURSIVE_WALK_CAN_FOLLOW = 2000;

    private static final int DEFAULT_PLATFORM_THREAD_STACK_SIZE = 1024 * 1024;

    @Test
    void shouldReturnAllSuccessorsWhenGraphIsDeeperThanTheStack() throws Exception {
        Flow flow = chainFlow(CHAIN_LONGER_THAN_A_RECURSIVE_WALK_CAN_FOLLOW);
        Execution execution = executionOnFirstTaskOf(flow);
        GraphCluster graph = GraphUtils.of(flow, execution);
        Set<String> from = Set.of(execution.getTaskRunList().getFirst().getId());

        AtomicReference<Set<AbstractGraph>> successors = new AtomicReference<>();
        Throwable caught = onFixedStackThread(() -> successors.set(GraphUtils.successors(graph, from)));

        assertThat(caught).as("walking a deep graph must not exhaust the stack").isNull();
        assertThat(successors.get()).hasSize(CHAIN_LONGER_THAN_A_RECURSIVE_WALK_CAN_FOLLOW + 1);
    }

    @Test
    void shouldThrowWhenGraphContainsCycle() {
        GraphCluster graph = new GraphCluster();
        GraphTask first = graphTask("first");
        GraphTask second = graphTask("second");
        GraphTask third = graphTask("third");
        graph.addNode(first);
        graph.addNode(second);
        graph.addNode(third);
        graph.addEdge(first, second, new Relation());
        graph.addEdge(second, third, new Relation());
        graph.addEdge(third, first, new Relation());

        Set<String> from = Set.of(first.getTaskRun().getId());

        assertThatThrownBy(() -> GraphUtils.successors(graph, from))
            .isInstanceOf(CyclicGraphException.class)
            .hasMessageContaining(first.getUid())
            .hasMessageContaining(second.getUid())
            .hasMessageContaining(third.getUid());
    }

    @Test
    void shouldThrowWhenATaskHasDuplicatedTaskRuns() throws Exception {
        Flow flow = chainFlow(3);
        String executionId = IdUtils.create();

        List<TaskRun> taskRuns = new ArrayList<>();
        taskRuns.add(taskRun(executionId, flow, "task_0"));
        TaskRun duplicated = taskRun(executionId, flow, "task_1");
        taskRuns.add(duplicated);
        taskRuns.add(taskRun(executionId, flow, "task_1"));
        taskRuns.add(taskRun(executionId, flow, "task_2"));

        Execution execution = Execution.newExecution(flow, List.of())
            .toBuilder()
            .id(executionId)
            .taskRunList(taskRuns)
            .build();

        GraphCluster graph = GraphUtils.of(flow, execution);

        assertThat(GraphUtils.edges(graph))
            .as("two task runs sharing a uid turn the chain edge between them into a self-loop")
            .anySatisfy(edge -> assertThat(edge.getSource()).isEqualTo(edge.getTarget()));

        assertThatThrownBy(() -> GraphUtils.successors(graph, Set.of(duplicated.getId())))
            .isInstanceOf(CyclicGraphException.class)
            .hasMessageContaining("root.task_1 -> root.task_1");
    }

    @Test
    void shouldNotThrowWhenGraphHasDiamondPaths() {
        GraphCluster graph = new GraphCluster();
        GraphTask start = graphTask("start");
        GraphTask left = graphTask("left");
        GraphTask right = graphTask("right");
        GraphTask join = graphTask("join");
        graph.addNode(start);
        graph.addNode(left);
        graph.addNode(right);
        graph.addNode(join);
        graph.addEdge(start, left, new Relation());
        graph.addEdge(start, right, new Relation());
        graph.addEdge(left, join, new Relation());
        graph.addEdge(right, join, new Relation());

        Set<AbstractGraph> successors = GraphUtils.successors(graph, Set.of(start.getTaskRun().getId()));

        assertThat(successors)
            .as("reaching the join node from two paths is not a cycle")
            .extracting(AbstractGraph::getUid)
            .contains(start.getUid(), left.getUid(), right.getUid());
    }

    @Test
    void shouldReturnOnlySuccessorsOfSelectedTaskRun() throws Exception {
        Flow flow = chainFlow(3);
        Execution execution = executionOnAllTasksOf(flow);
        GraphCluster graph = GraphUtils.of(flow, execution);
        TaskRun middle = execution.getTaskRunList().get(1);

        Set<AbstractGraph> successors = GraphUtils.successors(graph, Set.of(middle.getId()));

        assertThat(successors).extracting(AbstractGraph::getUid)
            .contains("root.task_1", "root.task_2")
            .doesNotContain("root.task_0");
    }

    private TaskRun taskRun(String executionId, Flow flow, String taskId) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .executionId(executionId)
            .tenantId(MAIN_TENANT)
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(taskId)
            .state(new State())
            .build();
    }

    private GraphTask graphTask(String id) {
        Task task = Log.builder()
            .id(id)
            .type(Log.class.getName())
            .message("hello")
            .build();

        TaskRun taskRun = TaskRun.builder()
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .tenantId(MAIN_TENANT)
            .namespace("io.kestra.tests")
            .flowId("cycle")
            .taskId(id)
            .state(new State())
            .build();

        return new GraphTask(task, taskRun, List.of(), null);
    }

    private Flow chainFlow(int size) {
        String tasks = IntStream.range(0, size)
            .mapToObj(i -> """
                  - id: task_%d
                    type: io.kestra.plugin.core.log.Log
                    message: hello
                """.formatted(i))
            .reduce("", String::concat);

        return YamlParser.parse(
            """
                id: deep-chain
                namespace: io.kestra.tests
                tasks:
                %s
                """.formatted(tasks),
            Flow.class
        );
    }

    private Execution executionOnFirstTaskOf(Flow flow) {
        return executionWithTaskRunsOn(flow, List.of(flow.getTasks().getFirst().getId()));
    }

    private Execution executionOnAllTasksOf(Flow flow) {
        return executionWithTaskRunsOn(flow, flow.getTasks().stream().map(Task::getId).toList());
    }

    private Execution executionWithTaskRunsOn(Flow flow, List<String> taskIds) {
        String executionId = IdUtils.create();

        List<TaskRun> taskRuns = taskIds.stream()
            .map(taskId -> taskRun(executionId, flow, taskId))
            .toList();

        return Execution.newExecution(flow, List.of())
            .toBuilder()
            .id(executionId)
            .taskRunList(taskRuns)
            .build();
    }

    private Throwable onFixedStackThread(Runnable runnable) throws InterruptedException {
        AtomicReference<Throwable> caught = new AtomicReference<>();

        Thread thread = new Thread(
            null,
            () ->
            {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    caught.set(t);
                }
            },
            "graph-walk",
            DEFAULT_PLATFORM_THREAD_STACK_SIZE
        );

        thread.start();
        thread.join();

        return caught.get();
    }
}
