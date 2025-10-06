package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.GraphService;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.Switch;
import io.kestra.plugin.core.trigger.Schedule;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class FlowGraphTest {

    @Inject
    private GraphService graphService;

    @Inject
    private TriggerRepositoryInterface triggerRepositoryInterface;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    void simple() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/return.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(5);
        assertThat(flowGraph.getEdges().size()).isEqualTo(4);
        assertThat(flowGraph.getClusters().size()).isZero();

        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getTask().getId()).isEqualTo("date");
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getValues()).isNull();

        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getTask().getId()).isEqualTo("task-id");
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getValues()).isNull();
    }

    @Test
    void sequentialNested() throws InternalException, IOException {
        FlowWithSource flow = this.parse("flows/valids/sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(19);
        assertThat(flowGraph.getEdges().size()).isEqualTo(18);
        assertThat(flowGraph.getClusters().size()).isEqualTo(3);

        assertThat(edge(flowGraph, ".*1-3-2-1").getTarget()).matches(".*1-3-2-2_end");
        assertThat(edge(flowGraph, ".*1-3-2-1").getRelation().getRelationType()).isEqualTo(RelationType.SEQUENTIAL);

        assertThat(edge(flowGraph, ".*1-seq").getTarget()).matches(".*1-1");
        assertThat(edge(flowGraph, ".*1-3-2_seq").getTarget()).matches(".*1-3-2-1");
    }

    @Test
    void errors() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/errors.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(17);
        assertThat(flowGraph.getEdges().size()).isEqualTo(17);
        assertThat(flowGraph.getClusters().size()).isEqualTo(4);

        assertThat(edge(flowGraph, cluster(flowGraph, "root").getStart(), ".*t2").getRelation().getRelationType()).isEqualTo(RelationType.ERROR);
        assertThat(edge(flowGraph, cluster(flowGraph, "root").getStart(), ".*failed").getRelation().getRelationType()).isNull();
    }

    @Test
    void parallel() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(12);
        assertThat(flowGraph.getEdges().size()).isEqualTo(16);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);

        assertThat(edge(flowGraph, ".*parent", ".*t2").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*parent", ".*t6").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);

        String parallelEnd = cluster(flowGraph, "root\\.parent").getEnd();
        assertThat(edge(flowGraph, ".*t1", parallelEnd).getSource()).matches(".*parent\\.t1");
        assertThat(edge(flowGraph, ".*t4", parallelEnd).getSource()).matches(".*parent\\.t4");
    }

    @Test
    void parallelNested() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(19);
        assertThat(flowGraph.getEdges().size()).isEqualTo(23);
        assertThat(flowGraph.getClusters().size()).isEqualTo(3);

        assertThat(edge(flowGraph, ".*1_par", ".*1-4_end").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*1_par", cluster(flowGraph, ".*1-3_par").getStart()).getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*1-3-2_par", ".*1-3-2-1").getRelation().getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
    }

    @Test
    void choice() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/switch.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(17);
        assertThat(flowGraph.getEdges().size()).isEqualTo(20);
        assertThat(flowGraph.getClusters().size()).isEqualTo(3);

        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.[^.]*").getRelation().getRelationType()).isEqualTo(RelationType.CHOICE);
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t3\\.[^.]*").getRelation().getValue()).isEqualTo("THIRD");
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t1").getRelation().getRelationType()).isEqualTo(RelationType.CHOICE);
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t1").getRelation().getValue()).isEqualTo("FIRST");
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.default").getRelation().getRelationType()).isEqualTo(RelationType.CHOICE);
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.default").getRelation().getValue()).isEqualTo("defaults");
        assertThat(edge(flowGraph, ".*t2", ".*t2_sub").getRelation().getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
    }

    @Test
    void each() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-sequential-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(13);
        assertThat(flowGraph.getEdges().size()).isEqualTo(12);
        assertThat(flowGraph.getClusters().size()).isEqualTo(2);

        assertThat(edge(flowGraph, ".*1-1_return", cluster(flowGraph, ".*1-2_each").getStart()).getRelation().getRelationType()).isEqualTo(RelationType.DYNAMIC);
        assertThat(edge(flowGraph, ".*1-2_each", ".*1-2-1_return").getRelation().getRelationType()).isEqualTo(RelationType.DYNAMIC);
    }

    @Test
    void eachParallel() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-parallel-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(11);
        assertThat(flowGraph.getEdges().size()).isEqualTo(10);
        assertThat(flowGraph.getClusters().size()).isEqualTo(2);

        assertThat(edge(flowGraph, ".*1_each", cluster(flowGraph, ".*2-1_seq").getStart()).getRelation().getRelationType()).isEqualTo(RelationType.DYNAMIC);
        assertThat(flowGraph.getClusters().get(1).getNodes().size()).isEqualTo(5);
    }

    @Test
    void allFlowable() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/all-flowable.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(38);
        assertThat(flowGraph.getEdges().size()).isEqualTo(42);
        assertThat(flowGraph.getClusters().size()).isEqualTo(7);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallelWithExecution(Execution execution) throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, execution);

        assertThat(flowGraph.getNodes().size()).isEqualTo(12);
        assertThat(flowGraph.getEdges().size()).isEqualTo(16);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);

        assertThat(edge(flowGraph, ".*parent", ".*t2").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*parent", ".*t6").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);

        assertThat(edge(flowGraph, ".*t1", ((GraphCluster) flowGraph.getClusters().getFirst().getCluster()).getEnd().getUid()).getSource()).matches(".*t1");
        assertThat(edge(flowGraph, ".*t4", ((GraphCluster) flowGraph.getClusters().getFirst().getCluster()).getEnd().getUid()).getSource()).matches(".*t4");

        assertThat(((AbstractGraphTask) node(flowGraph, "t1")).getTaskRun()).isNotNull();
        assertThat(((AbstractGraphTask) node(flowGraph, "t4")).getTaskRun()).isNotNull();
    }

    @Test
    @ExecuteFlow("flows/valids/each-sequential.yaml")
    void eachWithExecution(Execution execution) throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, execution);

        assertThat(flowGraph.getNodes().size()).isEqualTo(21);
        assertThat(flowGraph.getEdges().size()).isEqualTo(22);
        assertThat(flowGraph.getClusters().size()).isEqualTo(4);

        assertThat(edge(flowGraph, ".*1-1_value 1", ".*1-1_value 2").getRelation().getValue()).isEqualTo("value 2");
        assertThat(edge(flowGraph, ".*1-1_value 2", ".*1-1_value 3").getRelation().getValue()).isEqualTo("value 3");
        assertThat(edge(flowGraph, ".*1-2_value 3", cluster(flowGraph, ".*1_each\\.failed", "value 3").getEnd())).isNotNull();

        assertThat(edge(flowGraph, ".*failed_value 1", ".*1-2_value 1").getTarget()).matches(".*1-2_value 1");
    }

    @Test
    void trigger() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        FlowWithSource flow = this.parse("flows/valids/trigger-flow-listener.yaml");
        triggerRepositoryInterface.save(
            Trigger.of(flow, flow.getTriggers().getFirst()).toBuilder().disabled(true).build()
        );

        FlowGraph flowGraph = graphService.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(6);
        assertThat(flowGraph.getEdges().size()).isEqualTo(5);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);
        AbstractGraph triggerGraph = flowGraph.getNodes().stream().filter(e -> e instanceof GraphTrigger).findFirst().orElseThrow();
        assertThat(((GraphTrigger) triggerGraph).getTrigger().getDisabled()).isTrue();
    }

    @Test
    void multipleTriggers() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/trigger-flow-listener-no-inputs.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(7);
        assertThat(flowGraph.getEdges().size()).isEqualTo(7);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);
    }


    @Test
    void dag() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/dag.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(11);
        assertThat(flowGraph.getEdges().size()).isEqualTo(13);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);

        assertThat(edge(flowGraph, ".*root..*", ".*dag.root..*").getRelation().getRelationType()).isNull();
        assertThat(edge(flowGraph, ".*root.dag.*", ".*dag.task1.*").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*dag.task2.*", ".*dag.task4.*").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*dag.task2.*", ".*dag.task6.*").getRelation().getRelationType()).isEqualTo(RelationType.PARALLEL);
        assertThat(edge(flowGraph, ".*dag.task6", ".*dag.end.*").getRelation().getRelationType()).isNull();
        assertThat(edge(flowGraph, ".*dag.task5", ".*dag.end.*").getRelation().getRelationType()).isNull();
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/switch.yaml"}, tenantId = "tenant1")
    void subflow() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        FlowWithSource flow = this.parse("flows/valids/task-flow.yaml", "tenant1");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(6);
        assertThat(flowGraph.getEdges().size()).isEqualTo(5);
        assertThat(flowGraph.getClusters().size()).isEqualTo(1);

        flowGraph = graphService.flowGraph(flow, Collections.singletonList("root.launch"));

        assertThat(flowGraph.getNodes().size()).isEqualTo(23);
        assertThat(flowGraph.getEdges().size()).isEqualTo(26);
        assertThat(flowGraph.getClusters().size()).isEqualTo(5);

        assertThat(((SubflowGraphTask) ((SubflowGraphCluster) cluster(flowGraph, "root\\.launch").getCluster()).getTaskNode()).executableTask().subflowId().flowId()).isEqualTo("switch");
        SubflowGraphTask subflowGraphTask = (SubflowGraphTask) nodeByUid(flowGraph, "root.launch");
        assertThat(subflowGraphTask.getTask()).isInstanceOf(SubflowGraphTask.SubflowTaskWrapper.class);
        assertThat(subflowGraphTask.getRelationType()).isEqualTo(RelationType.SEQUENTIAL);

        GraphTask switchNode = (GraphTask) nodeByUid(flowGraph, "root.launch.parent-seq");
        assertThat(switchNode.getTask()).isInstanceOf(Switch.class);
        assertThat(switchNode.getRelationType()).isEqualTo(RelationType.CHOICE);

        GraphTrigger flowTrigger = (GraphTrigger) nodeByUid(flowGraph, "root.Triggers.schedule");
        assertThat(flowTrigger.getTriggerDeclaration()).isInstanceOf(Schedule.class);
        GraphTrigger subflowTrigger = (GraphTrigger) nodeByUid(flowGraph, "root.launch.Triggers.schedule");
        assertThat(subflowTrigger.getTriggerDeclaration()).isInstanceOf(Schedule.class);
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow-dynamic.yaml",
        "flows/valids/switch.yaml"}, tenantId = "tenant2")
    void dynamicIdSubflow() throws IllegalVariableEvaluationException, TimeoutException, QueueException, IOException, FlowProcessingException {
        FlowWithSource flow = this.parse("flows/valids/task-flow-dynamic.yaml", "tenant2").toBuilder().revision(1).build();

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> graphService.flowGraph(flow, Collections.singletonList("root.launch")));
        assertThat(illegalArgumentException.getMessage()).isEqualTo("Can't expand subflow task 'launch' because namespace and/or flowId contains dynamic values. This can only be viewed on an execution.");

        Execution execution = runnerUtils.runOne("tenant2", "io.kestra.tests", "task-flow-dynamic", 1, (f, e) -> Map.of(
            "namespace", f.getNamespace(),
            "flowId", "switch"
        ));
        FlowGraph flowGraph = graphService.flowGraph(flow, Collections.singletonList("root.launch"), execution);

        assertThat(flowGraph.getNodes().size()).isEqualTo(20);
        assertThat(flowGraph.getEdges().size()).isEqualTo(23);
        assertThat(flowGraph.getClusters().size()).isEqualTo(4);
    }

    @Test
    void finallySequential() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(13);
        assertThat(flowGraph.getEdges().size()).isEqualTo(13);
        assertThat(flowGraph.getClusters().size()).isEqualTo(2);

        assertThat(edge(flowGraph, ".*seq.finally.*", ".*seq.a1").getRelation().getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
        assertThat(edge(flowGraph, ".*seq.a1", ".*seq.a2").getRelation().getRelationType()).isEqualTo(RelationType.FINALLY);
        assertThat(edge(flowGraph, ".*seq.a2", ".*seq.end.*").getRelation().getRelationType()).isNull();
    }

    @Test
    void finallySequentialError() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-sequential-error.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(15);
        assertThat(flowGraph.getEdges().size()).isEqualTo(16);
        assertThat(flowGraph.getClusters().size()).isEqualTo(2);

        assertThat(edge(flowGraph, ".*seq.e1", ".*seq.e2").getRelation().getRelationType()).isEqualTo(RelationType.ERROR);
        assertThat(edge(flowGraph, ".*seq.e2", ".*seq.finally.*").getRelation().getRelationType()).isNull();
        assertThat(edge(flowGraph, ".*seq.finally.*", ".*seq.a1").getRelation().getRelationType()).isEqualTo(RelationType.SEQUENTIAL);
        assertThat(edge(flowGraph, ".*seq.a1", ".*seq.a2").getRelation().getRelationType()).isEqualTo(RelationType.FINALLY);
        assertThat(edge(flowGraph, ".*seq.a2", ".*seq.end.*").getRelation().getRelationType()).isNull();
    }

    @Test
    void finallyDag() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-dag.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(17);
        assertThat(flowGraph.getEdges().size()).isEqualTo(18);
        assertThat(flowGraph.getClusters().size()).isEqualTo(2);

        assertThat(edge(flowGraph, ".*dag.e1", ".*dag.e2").getRelation().getRelationType()).isEqualTo(RelationType.ERROR);
        assertThat(edge(flowGraph, ".*dag.e2", ".*dag.finally.*").getRelation().getRelationType()).isNull();
        assertThat(edge(flowGraph, ".*dag.t3.end..*", ".*dag.finally.*").getRelation().getRelationType()).isNull();
        assertThat(edge(flowGraph, ".*dag.finally.*", ".*dag.a1").getRelation().getRelationType()).isEqualTo(RelationType.DYNAMIC);
        assertThat(edge(flowGraph, ".*dag.a1", ".*dag.a2").getRelation().getRelationType()).isEqualTo(RelationType.DYNAMIC);
        assertThat(edge(flowGraph, ".*dag.a2", ".*dag.end.*").getRelation().getRelationType()).isNull();
    }

    @Test
    void afterExecutionSequential() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/after-execution.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size()).isEqualTo(5);
        assertThat(flowGraph.getEdges().size()).isEqualTo(4);

        assertThat(edge(flowGraph, "root.root.*", "root.mytask.*")).isNotNull();
        assertThat(edge(flowGraph, "root.mytask.*", "root.after-execution.*")).isNotNull();
        assertThat(edge(flowGraph, "root.after-execution.*", "root.end.*")).isNotNull();
    }

    private FlowWithSource parse(String path) throws IOException {
        return parse(path, MAIN_TENANT);
    }

    private FlowWithSource parse(String path, String tenantId) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, FlowWithSource.class).toBuilder()
            .tenantId(tenantId)
            .source(Files.readString(file.toPath()))
            .build();
    }

    private static AbstractGraph node(FlowGraph flowGraph, String taskId) {
        return flowGraph
            .getNodes()
            .stream()
            .filter(e -> e instanceof AbstractGraphTask)
            .filter(e -> ((AbstractGraphTask) e).getTask() != null && ((AbstractGraphTask) e).getTask().getId().equals(taskId))
            .findFirst()
            .orElseThrow();
    }

    private static AbstractGraph nodeByUid(FlowGraph flowGraph, String uid) {
        return flowGraph
            .getNodes()
            .stream()
            .filter(e -> e.getUid().equals(uid))
            .findFirst()
            .orElseThrow();
    }

    private static FlowGraph.Edge edge(FlowGraph flowGraph, String source) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source))
            .findFirst()
            .orElseThrow();
    }

    private static FlowGraph.Edge edge(FlowGraph flowGraph, String source, String target) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source) && e.getTarget().matches(target))
            .findFirst()
            .orElseThrow();
    }

    private static List<String> edges(FlowGraph flowGraph, String source) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source))
            .map(FlowGraph.Edge::getTarget)
            .toList();
    }

    private static FlowGraph.Cluster cluster(FlowGraph flowGraph, String clusterIdRegex) {
        return cluster(flowGraph, clusterIdRegex, null);
    }

    private static FlowGraph.Cluster cluster(FlowGraph flowGraph, String clusterIdRegex, String value) {
        if(clusterIdRegex.equals("root")) {
            String[] startEnd = new String[2];
            flowGraph.getNodes().forEach(n -> {
                if(!n.getUid().matches("root\\.[^.]*")) {
                    return;
                }

                if(n.getType().endsWith("GraphClusterRoot")) {
                    startEnd[0] = n.getUid();
                } else if(n.getType().endsWith("GraphClusterEnd")) {
                    startEnd[1] = n.getUid();
                }
            });
            return new FlowGraph.Cluster(null, null, null, startEnd[0], startEnd[1]);
        }
        return flowGraph
            .getClusters()
            .stream()
            .filter(e -> e.getCluster().uid.matches(clusterIdRegex)
                && (value == null || e.getNodes().stream().anyMatch(n -> n.matches(e.getCluster().uid + "_" + value))))
            .findFirst()
            .orElseThrow();
    }
}
