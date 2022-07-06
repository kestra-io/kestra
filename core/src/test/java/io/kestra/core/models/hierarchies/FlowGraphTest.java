package io.kestra.core.models.hierarchies;

import org.junit.jupiter.api.Test;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FlowGraphTest extends AbstractMemoryRunnerTest {
    @Inject
    private YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void simple() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/return.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(5));
        assertThat(flowGraph.getEdges().size(), is(4));
        assertThat(flowGraph.getClusters().size(), is(0));

        assertThat(flowGraph.getNodes().get(2).getTask().getId(), is("date"));
        assertThat(flowGraph.getNodes().get(2).getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(flowGraph.getNodes().get(2).getValues(), is(nullValue()));

        assertThat(flowGraph.getNodes().get(3).getTask().getId(), is("task-id"));
        assertThat(flowGraph.getNodes().get(3).getRelationType(), is(RelationType.SEQUENTIAL));
        assertThat(flowGraph.getNodes().get(3).getValues(), is(nullValue()));
    }

    @Test
    void sequentialNested() throws InternalException {
        Flow flow = this.parse("flows/valids/sequential.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(16));
        assertThat(flowGraph.getEdges().size(), is(15));
        assertThat(flowGraph.getClusters().size(), is(3));

        assertThat(edge(flowGraph, "1-3-2-1").getTarget(), is("1-3-2-2_end"));
        assertThat(edge(flowGraph, "1-3-2-1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));

        assertThat(edge(flowGraph, "1-seq.*_end").getTarget(), is("2_end"));
        assertThat(edge(flowGraph, "1-3-2_seq.*_end").getTarget(), is("1-3-3_end"));
    }

    @Test
    void errors() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/errors.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(13));
        assertThat(flowGraph.getEdges().size(), is(13));
        assertThat(flowGraph.getClusters().size(), is(4));

        assertThat(edges(flowGraph, flowGraph.getNodes().get(0).getUid()), containsInAnyOrder("failed", "t2"));
        assertThat(edge(flowGraph, flowGraph.getNodes().get(0).getUid(), "t2").getRelation().getRelationType(), is(RelationType.ERROR));
    }

    @Test
    void parallel() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(11));
        assertThat(flowGraph.getEdges().size(), is(15));
        assertThat(flowGraph.getClusters().size(), is(1));

        assertThat(edge(flowGraph, "parent_.*_root", "t2").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, "parent_.*_root", "t6").getRelation().getRelationType(), is(RelationType.PARALLEL));

        assertThat(edge(flowGraph, "t1", "parent_.*_end").getSource(), is("t1"));
        assertThat(edge(flowGraph, "t4", "parent_.*_end").getSource(), is("t4"));
    }

    @Test
    void parallelNested() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/parallel-nested.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(16));
        assertThat(flowGraph.getEdges().size(), is(20));
        assertThat(flowGraph.getClusters().size(), is(3));

        assertThat(flowGraph.getClusters().get(2).getParents(), containsInRelativeOrder("1_par", "1-3_par"));

        assertThat(edge(flowGraph, "1_par_.*_root", "1-4_end").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, "1_par_.*_root", "1-3_par_.*_root").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, "1-3-2_par_.*_root", "1-3-2-1").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void choice() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/switch.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(12));
        assertThat(flowGraph.getEdges().size(), is(15));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, "parent-seq_.*_root", "t3_.*_root").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, "parent-seq_.*_root", "t3_.*_root").getRelation().getValue(), is("THIRD"));
        assertThat(edge(flowGraph, "parent-seq_.*_root", "t1").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, "parent-seq_.*_root", "t1").getRelation().getValue(), is("FIRST"));
        assertThat(edge(flowGraph, "parent-seq_.*_root", "default").getRelation().getRelationType(), is(RelationType.CHOICE));
        assertThat(edge(flowGraph, "parent-seq_.*_root", "default").getRelation().getValue(), is("defaults"));
        assertThat(edge(flowGraph, "t2", "t2_sub").getRelation().getRelationType(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void each() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/each-sequential-nested.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(11));
        assertThat(flowGraph.getEdges().size(), is(10));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, "1-1_return", "1-2_each_.*_root").getRelation().getRelationType(), is(RelationType.DYNAMIC));
        assertThat(edge(flowGraph, "1-2_each_.*_root", "1-2-1_return").getRelation().getRelationType(), is(RelationType.DYNAMIC));
    }

    @Test
    void eachParralel() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/each-parallel-nested.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(9));
        assertThat(flowGraph.getEdges().size(), is(8));
        assertThat(flowGraph.getClusters().size(), is(2));

        assertThat(edge(flowGraph, "1_each_.*_root", "2-1_seq_.*_root").getRelation().getRelationType(), is(RelationType.DYNAMIC));
    }

    @Test
    void allFlowable() throws IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/all-flowable.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow);

        assertThat(flowGraph.getNodes().size(), is(28));
        assertThat(flowGraph.getEdges().size(), is(31));
        assertThat(flowGraph.getClusters().size(), is(6));
    }

    @Test
    void parralelWithExecution() throws TimeoutException, IllegalVariableEvaluationException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "parallel");

        Flow flow = this.parse("flows/valids/parallel.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow, execution);

        assertThat(flowGraph.getNodes().size(), is(11));
        assertThat(flowGraph.getEdges().size(), is(15));
        assertThat(flowGraph.getClusters().size(), is(1));

        assertThat(edge(flowGraph, "parent_.*_root", "t2").getRelation().getRelationType(), is(RelationType.PARALLEL));
        assertThat(edge(flowGraph, "parent_.*_root", "t6").getRelation().getRelationType(), is(RelationType.PARALLEL));

        assertThat(edge(flowGraph, "t1", "parent_.*_end").getSource(), is("t1"));
        assertThat(edge(flowGraph, "t4", "parent_.*_end").getSource(), is("t4"));

        assertThat(node(flowGraph, "t1").getTaskRun(), is(notNullValue()));
        assertThat(node(flowGraph, "t4").getTaskRun(), is(notNullValue()));
    }

    @Test
    void eachWithExecution() throws TimeoutException, IllegalVariableEvaluationException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-sequential");

        Flow flow = this.parse("flows/valids/each-sequential.yaml");
        FlowGraph flowGraph = FlowGraph.of(flow, execution);

        assertThat(flowGraph.getNodes().size(), is(17));
        assertThat(flowGraph.getEdges().size(), is(18));
        assertThat(flowGraph.getClusters().size(), is(4));

        assertThat(edge(flowGraph, "1-1_value 1", "1-1_value 2").getRelation().getValue(), is("value 2"));
        assertThat(edge(flowGraph, "1-1_value 2", "1-1_value 3").getRelation().getValue(), is("value 3"));
        assertThat(edge(flowGraph, "1-2_value 3", "failed_value 3_.*_end"), is(notNullValue()));

        assertThat(edge(flowGraph, "failed_value 1_.*","failed_value 2_.*_root").getRelation().getValue(), is("value 2"));
        assertThat(edge(flowGraph, "1-1_value 2","1-1_value 3").getRelation().getValue(), is("value 3"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }

    private AbstractGraphTask node(FlowGraph flowGraph, String taskId) {
        return flowGraph
            .getNodes()
            .stream()
            .filter(e -> e.getTask() != null && e.getTask().getId().equals(taskId))
            .findFirst()
            .orElseThrow();
    }

    private FlowGraph.Edge edge(FlowGraph flowGraph, String source) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source))
            .findFirst()
            .orElseThrow();
    }

    private FlowGraph.Edge edge(FlowGraph flowGraph, String source, String target) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source) && e.getTarget().matches(target))
            .findFirst()
            .orElseThrow();
    }

    private List<String> edges(FlowGraph flowGraph, String source) {
        return flowGraph
            .getEdges()
            .stream()
            .filter(e -> e.getSource().matches(source))
            .map(FlowGraph.Edge::getTarget)
            .collect(Collectors.toList());
    }
}
