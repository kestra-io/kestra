package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest(startRunner = true)
class FlowGraphTest {
    @Inject
    private YamlParser yamlParser = new YamlParser();

    @Inject
    private GraphService graphService;

    @Inject
    private TriggerRepositoryInterface triggerRepositoryInterface;

    @Inject
    private RunnerUtils runnerUtils;

    @Test
    void simple() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/return.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(5));
        assertThat(flowGraph.getEdges().size(), is(4));
        assertThat(flowGraph.getClusters().size(), is(0));

        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getTask().getId(), is("date"));
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(2)).getValues(), is(nullValue()));

        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getTask().getId(), is("task-id"));
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(((AbstractGraphTask) flowGraph.getNodes().get(3)).getValues(), is(nullValue()));
    }

    @Test
    void sequentialNested() throws InternalException, IOException {
        FlowWithSource flow = this.parse("flows/valids/sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(19));
        assertThat(flowGraph.getEdges().size(), is(18));
        assertThat(flowGraph.getClusters().size(), is(3));

        assertThat(edge(flowGraph, ".*1-3-2-1").getTarget(), matchesPattern(".*1-3-2-2_end"));
        assertThat(edge(flowGraph, ".*1-3-2-1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));

        assertThat(edge(flowGraph, ".*1-seq").getTarget(), matchesPattern(".*1-1"));
        assertThat(edge(flowGraph, ".*1-3-2_seq").getTarget(), matchesPattern(".*1-3-2-1"));
    }

    @Test
    void errors() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/errors.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(17));
        assertThat(flowGraph.getEdges().size(), is(17));
        assertThat(flowGraph.getClusters().size(), is(4));

        assertThat(edge(flowGraph, cluster(flowGraph, "root").getStart(), ".*t2").getRelation().getRelationType(), is(RelationType.ERROR));
        assertThat(edge(flowGraph, cluster(flowGraph, "root").getStart(), ".*failed").getRelation().getRelationType(), is(nullValue()));
    }

    @Test
    void parallel() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(12));
        assertThat(flowGraph.getEdges().size(), is(16));
        assertThat(flowGraph.getClusters().size(), is(1));

        assertThat(edge(flowGraph, ".*parent", ".*t2").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*parent", ".*t6").getRelation().getRelationType(), is(RelationType.PARALLEL));

        String parallelEnd = cluster(flowGraph, "root\\.parent").getEnd();
        assertThat(edge(flowGraph, ".*t1", parallelEnd).getSource(), matchesPattern(".*parent\\.t1"));
        assertThat(edge(flowGraph, ".*t4", parallelEnd).getSource(), matchesPattern(".*parent\\.t4"));
    }

    @Test
    void parallelNested() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(19));
        assertThat(flowGraph.getEdges().size(), is(23));
        assertThat(flowGraph.getClusters().size(), is(3));

        assertThat(edge(flowGraph, ".*1_par", ".*1-4_end").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*1_par", cluster(flowGraph, ".*1-3_par").getStart()).getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*1-3-2_par", ".*1-3-2-1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void choice() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/switch.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(17));
        assertThat(flowGraph.getEdges().size(), is(20));
        assertThat(flowGraph.getClusters().size(), is(3));

        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.[^.]*").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t3\\.[^.]*").getRelation().getValue(), is("THIRD"));
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t1").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.t1").getRelation().getValue(), is("FIRST"));
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.default").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, ".*parent-seq", ".*parent-seq\\.default").getRelation().getValue(), is("defaults"));
        assertThat(edge(flowGraph, ".*t2", ".*t2_sub").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void each() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-sequential-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(13));
        assertThat(flowGraph.getEdges().size(), is(12));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, ".*1-1_return", cluster(flowGraph, ".*1-2_each").getStart()).getRelation().getRelationType(), is(RelationType.DYNAMIC));
        assertThat(edge(flowGraph, ".*1-2_each", ".*1-2-1_return").getRelation().getRelationType(), is(RelationType.DYNAMIC));
    }

    @Test
    void eachParallel() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-parallel-nested.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(11));
        assertThat(flowGraph.getEdges().size(), is(10));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, ".*1_each", cluster(flowGraph, ".*2-1_seq").getStart()).getRelation().getRelationType(), is(RelationType.DYNAMIC));
        assertThat(flowGraph.getClusters().get(1).getNodes().size(), is(5));
    }

    @Test
    void allFlowable() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/all-flowable.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(38));
        assertThat(flowGraph.getEdges().size(), is(42));
        assertThat(flowGraph.getClusters().size(), is(7));
    }

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallelWithExecution(Execution execution) throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, execution);

        assertThat(flowGraph.getNodes().size(), is(12));
        assertThat(flowGraph.getEdges().size(), is(16));
        assertThat(flowGraph.getClusters().size(), is(1));

        assertThat(edge(flowGraph, ".*parent", ".*t2").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*parent", ".*t6").getRelation().getRelationType(), is(RelationType.PARALLEL));

        assertThat(edge(flowGraph, ".*t1", ((GraphCluster) flowGraph.getClusters().getFirst().getCluster()).getEnd().getUid()).getSource(), matchesPattern(".*t1"));
        assertThat(edge(flowGraph, ".*t4", ((GraphCluster) flowGraph.getClusters().getFirst().getCluster()).getEnd().getUid()).getSource(), matchesPattern(".*t4"));

        assertThat(((AbstractGraphTask) node(flowGraph, "t1")).getTaskRun(), is(notNullValue()));
        assertThat(((AbstractGraphTask) node(flowGraph, "t4")).getTaskRun(), is(notNullValue()));
    }

    @Test
    @ExecuteFlow("flows/valids/each-sequential.yaml")
    void eachWithExecution(Execution execution) throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/each-sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, execution);

        assertThat(flowGraph.getNodes().size(), is(21));
        assertThat(flowGraph.getEdges().size(), is(22));
        assertThat(flowGraph.getClusters().size(), is(4));

        assertThat(edge(flowGraph, ".*1-1_value 1", ".*1-1_value 2").getRelation().getValue(), is("value 2"));
        assertThat(edge(flowGraph, ".*1-1_value 2", ".*1-1_value 3").getRelation().getValue(), is("value 3"));
        assertThat(edge(flowGraph, ".*1-2_value 3", cluster(flowGraph, ".*1_each\\.failed", "value 3").getEnd()), is(notNullValue()));

        assertThat(edge(flowGraph, ".*failed_value 1", ".*1-2_value 1").getTarget(), matchesPattern(".*1-2_value 1"));
    }

    @Test
    void trigger() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/trigger-flow-listener.yaml");
        triggerRepositoryInterface.save(
            Trigger.of(flow, flow.getTriggers().getFirst()).toBuilder().disabled(true).build()
        );

        FlowGraph flowGraph = graphService.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(6));
        assertThat(flowGraph.getEdges().size(), is(5));
        assertThat(flowGraph.getClusters().size(), is(1));
        AbstractGraph triggerGraph = flowGraph.getNodes().stream().filter(e -> e instanceof GraphTrigger).findFirst().orElseThrow();
        assertThat(((GraphTrigger) triggerGraph).getTrigger().getDisabled(), is(true));
    }

    @Test
    void multipleTriggers() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/trigger-flow-listener-no-inputs.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(7));
        assertThat(flowGraph.getEdges().size(), is(7));
        assertThat(flowGraph.getClusters().size(), is(1));
    }


    @Test
    void dag() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/dag.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(11));
        assertThat(flowGraph.getEdges().size(), is(13));
        assertThat(flowGraph.getClusters().size(), is(1));

        assertThat(edge(flowGraph, ".*root..*", ".*dag.root..*").getRelation().getRelationType(), is(nullValue()));
        assertThat(edge(flowGraph, ".*root.dag.*", ".*dag.task1.*").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*dag.task2.*", ".*dag.task4.*").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*dag.task2.*", ".*dag.task6.*").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, ".*dag.task6", ".*dag.end.*").getRelation().getRelationType(), is(nullValue()));
        assertThat(edge(flowGraph, ".*dag.task5", ".*dag.end.*").getRelation().getRelationType(), is(nullValue()));
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/switch.yaml"})
    void subflow() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/task-flow.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(6));
        assertThat(flowGraph.getEdges().size(), is(5));
        assertThat(flowGraph.getClusters().size(), is(1));

        flowGraph = graphService.flowGraph(flow, Collections.singletonList("root.launch"));

        assertThat(flowGraph.getNodes().size(), is(23));
        assertThat(flowGraph.getEdges().size(), is(26));
        assertThat(flowGraph.getClusters().size(), is(5));

        assertThat(((SubflowGraphTask) ((SubflowGraphCluster) cluster(flowGraph, "root\\.launch").getCluster()).getTaskNode()).executableTask().subflowId().flowId(), is("switch"));
        SubflowGraphTask subflowGraphTask = (SubflowGraphTask) nodeByUid(flowGraph, "root.launch");
        assertThat(subflowGraphTask.getTask(), instanceOf(SubflowGraphTask.SubflowTaskWrapper.class));
        assertThat(subflowGraphTask.getRelationType(), is(RelationType.SEQUENTIAL));

        GraphTask switchNode = (GraphTask) nodeByUid(flowGraph, "root.launch.parent-seq");
        assertThat(switchNode.getTask(), instanceOf(Switch.class));
        assertThat(switchNode.getRelationType(), is(RelationType.CHOICE));

        GraphTrigger flowTrigger = (GraphTrigger) nodeByUid(flowGraph, "root.Triggers.schedule");
        assertThat(flowTrigger.getTriggerDeclaration(), instanceOf(Schedule.class));
        GraphTrigger subflowTrigger = (GraphTrigger) nodeByUid(flowGraph, "root.launch.Triggers.schedule");
        assertThat(subflowTrigger.getTriggerDeclaration(), instanceOf(Schedule.class));
    }

    @Test
    @LoadFlows({"flows/valids/task-flow-dynamic.yaml",
        "flows/valids/switch.yaml"})
    void dynamicIdSubflow() throws IllegalVariableEvaluationException, TimeoutException, QueueException, IOException {
        FlowWithSource flow = this.parse("flows/valids/task-flow-dynamic.yaml").toBuilder().revision(1).build();

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> graphService.flowGraph(flow, Collections.singletonList("root.launch")));
        assertThat(illegalArgumentException.getMessage(), is("Can't expand subflow task 'launch' because namespace and/or flowId contains dynamic values. This can only be viewed on an execution."));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-flow-dynamic", 1, (f, e) -> Map.of(
            "namespace", f.getNamespace(),
            "flowId", "switch"
        ));
        FlowGraph flowGraph = graphService.flowGraph(flow, Collections.singletonList("root.launch"), execution);

        assertThat(flowGraph.getNodes().size(), is(20));
        assertThat(flowGraph.getEdges().size(), is(23));
        assertThat(flowGraph.getClusters().size(), is(4));
    }

    @Test
    void finallySequential() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-sequential.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(13));
        assertThat(flowGraph.getEdges().size(), is(13));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, ".*seq.finally.*", ".*seq.a1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(edge(flowGraph, ".*seq.a1", ".*seq.a2").getRelation().getRelationType(), is(RelationType.FINALLY));
        assertThat(edge(flowGraph, ".*seq.a2", ".*seq.end.*").getRelation().getRelationType(), is(nullValue()));
    }

    @Test
    void finallySequentialError() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-sequential-error.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(15));
        assertThat(flowGraph.getEdges().size(), is(16));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, ".*seq.e1", ".*seq.e2").getRelation().getRelationType(), is(RelationType.ERROR));
        assertThat(edge(flowGraph, ".*seq.e2", ".*seq.finally.*").getRelation().getRelationType(), is(nullValue()));
        assertThat(edge(flowGraph, ".*seq.finally.*", ".*seq.a1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(edge(flowGraph, ".*seq.a1", ".*seq.a2").getRelation().getRelationType(), is(RelationType.FINALLY));
        assertThat(edge(flowGraph, ".*seq.a2", ".*seq.end.*").getRelation().getRelationType(), is(nullValue()));
    }

    @Test
    void finallyDag() throws IllegalVariableEvaluationException, IOException {
        FlowWithSource flow = this.parse("flows/valids/finally-dag.yaml");
        FlowGraph flowGraph = GraphUtils.flowGraph(flow, null);

        assertThat(flowGraph.getNodes().size(), is(17));
        assertThat(flowGraph.getEdges().size(), is(18));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, ".*dag.e1", ".*dag.e2").getRelation().getRelationType(), is(RelationType.ERROR));
        assertThat(edge(flowGraph, ".*dag.e2", ".*dag.finally.*").getRelation().getRelationType(), is(nullValue()));
        assertThat(edge(flowGraph, ".*dag.t3.end..*", ".*dag.finally.*").getRelation().getRelationType(), is(nullValue()));
        assertThat(edge(flowGraph, ".*dag.finally.*", ".*dag.a1").getRelation().getRelationType(), is(RelationType.DYNAMIC));
        assertThat(edge(flowGraph, ".*dag.a1", ".*dag.a2").getRelation().getRelationType(), is(RelationType.DYNAMIC));
        assertThat(edge(flowGraph, ".*dag.a2", ".*dag.end.*").getRelation().getRelationType(), is(nullValue()));
    }

    private FlowWithSource parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlParser.parse(file, Flow.class).withSource(Files.readString(file.toPath()));
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
