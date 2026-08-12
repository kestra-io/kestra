package io.kestra.core.models.hierarchies;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractGraphTest {
    @Test
    void shouldNotBeEqualWhenHashCodesCollide() {
        AbstractGraph first = new CollidingHashCodeGraph("first");
        AbstractGraph second = new CollidingHashCodeGraph("second");

        assertThat(first).as("equality must not be delegated to the hash code").isNotEqualTo(second);
        assertThat(second).as("equality must not be delegated to the hash code").isNotEqualTo(first);
    }

    @Test
    void shouldBeEqualToTheNodeItStandsInFor() {
        GraphTask original = graphTask("original");
        AbstractGraph standIn = new StandInGraph("rendered", original);

        assertThat(standIn).isEqualTo(original);
        assertThat(original).isEqualTo(standIn);
        assertThat(standIn).hasSameHashCodeAs(original);
    }

    @Test
    void shouldNotBeEqualWhenUidIsEqualButNodesDiffer() {
        GraphTask first = graphTask("same");
        GraphTask second = graphTask("same");

        assertThat(first).as("a node is identified by nodeIdentity(), not by its mutable uid").isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    @Test
    void shouldNotContainDuplicateUidsWhenATaskHasDuplicatedTaskRuns() throws Exception {
        List<AbstractGraph> nodes = GraphUtils.nodes(GraphUtils.of(chainFlow(), executionWithDuplicatedTaskRun()));

        assertThat(nodes).extracting(AbstractGraph::getUid).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotContainSelfLoopWhenATaskHasDuplicatedTaskRuns() throws Exception {
        List<FlowGraph.Edge> edges = GraphUtils.edges(GraphUtils.of(chainFlow(), executionWithDuplicatedTaskRun()));

        assertThat(edges)
            .as("two nodes sharing a uid would make the chain edge between them a self-loop")
            .noneSatisfy(edge -> assertThat(edge.getSource()).isEqualTo(edge.getTarget()));
    }

    private GraphTask graphTask(String uid) {
        Task task = Log.builder()
            .id(uid)
            .type(Log.class.getName())
            .message("hello")
            .build();

        return new GraphTask(task, null, List.of(), null);
    }

    private Flow chainFlow() {
        return YamlParser.parse("""
            id: chain
            namespace: io.kestra.tests
            tasks:
              - id: task_0
                type: io.kestra.plugin.core.log.Log
                message: hello
              - id: task_1
                type: io.kestra.plugin.core.log.Log
                message: hello
              - id: task_2
                type: io.kestra.plugin.core.log.Log
                message: hello
            """, Flow.class);
    }

    private Execution executionWithDuplicatedTaskRun() {
        Flow flow = chainFlow();
        String executionId = IdUtils.create();

        List<TaskRun> taskRuns = new ArrayList<>();
        taskRuns.add(taskRun(executionId, flow, "task_0"));
        taskRuns.add(taskRun(executionId, flow, "task_1"));
        taskRuns.add(taskRun(executionId, flow, "task_1"));
        taskRuns.add(taskRun(executionId, flow, "task_2"));

        return Execution.newExecution(flow, List.of())
            .toBuilder()
            .id(executionId)
            .taskRunList(taskRuns)
            .build();
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

    private static final class CollidingHashCodeGraph extends AbstractGraph {
        private CollidingHashCodeGraph(String uid) {
            super(uid);
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    private static final class StandInGraph extends AbstractGraph {
        private final AbstractGraph standsInFor;

        private StandInGraph(String uid, AbstractGraph standsInFor) {
            super(uid);
            this.standsInFor = standsInFor;
        }

        @Override
        protected AbstractGraph nodeIdentity() {
            return standsInFor;
        }
    }
}
