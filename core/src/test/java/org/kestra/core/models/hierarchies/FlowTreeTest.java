package org.kestra.core.models.hierarchies;

import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.serializers.YamlFlowParser;
import org.kestra.core.utils.TestsUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class FlowTreeTest extends AbstractMemoryRunnerTest {
    @Inject
    private YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void simple() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/return.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(3));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));

        assertThat(flowTree.getTasks().get(1).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getId(), is("date"));
        assertThat(flowTree.getTasks().get(1).getGroups().size(), is(0));
        assertThat(flowTree.getTasks().get(1).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(1).getGroups().size(), is(0));

        assertThat(flowTree.getTasks().get(2).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(2).getParent().get(0).getId(), is("task-id"));
        assertThat(flowTree.getTasks().get(2).getRelation(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void sequentialNested() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/sequential.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(11));

        assertThat(flowTree.getTasks().get(5).getTask().getId(), is("1-3-2_seq"));
        assertThat(flowTree.getTasks().get(5).getGroups(), containsInAnyOrder("1-3_seq", "1-seq"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("1-3-2-1"));
        assertThat(flowTree.getTasks().get(6).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("1-3-2_seq"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(7).getGroups(), containsInAnyOrder("1-3-2_seq", "1-3_seq", "1-seq"));

        assertThat(flowTree.getTasks().get(7).getTask().getId(), is("1-3-2-2_end"));
        assertThat(flowTree.getTasks().get(7).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(7).getParent().get(0).getId(), is("1-3-2-1"));
        assertThat(flowTree.getTasks().get(7).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(7).getGroups(), containsInAnyOrder("1-3-2_seq", "1-3_seq", "1-seq"));
    }

    @Test
    void errors() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/errors.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(7));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("t2"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.ERROR));

        assertThat(flowTree.getTasks().get(1).getTask().getId(), is("t3"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getId(), is("t2"));
        assertThat(flowTree.getTasks().get(1).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(5).getTask().getId(), is("t3-t1-t1-t1-last"));
        assertThat(flowTree.getTasks().get(5).getGroups(), containsInAnyOrder("t3", "t3-t1", "t3-t1-t1", "t3-t1-t1-t1"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("failed"));
        assertThat(flowTree.getTasks().get(6).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void parallel() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/parallel.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(8));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("parent"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("t3"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("parent"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.PARALLEL));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("t6"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("parent"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.PARALLEL));

        assertThat(flowTree.getTasks().get(7).getTask().getId(), is("last"));
        assertThat(flowTree.getTasks().get(7).getParent().get(0).getId(), is("parent"));
        assertThat(flowTree.getTasks().get(7).getRelation(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void parallelNested() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/parallel-nested.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(11));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("1_par"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("1-3_par"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("1_par"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.PARALLEL));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("1-3-2-1"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("1-3-2_par"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(10).getTask().getId(), is("2_end"));
        assertThat(flowTree.getTasks().get(10).getParent().get(0).getId(), is("1_par"));
        assertThat(flowTree.getTasks().get(10).getRelation(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void choice() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/switch.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(8));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("parent-seq"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("t2"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("parent-seq"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.CHOICE));

        assertThat(flowTree.getTasks().get(4).getTask().getId(), is("t2_sub"));
        assertThat(flowTree.getTasks().get(4).getParent().get(0).getId(), is("t2"));
        assertThat(flowTree.getTasks().get(4).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("error-t1"));
        assertThat(flowTree.getTasks().get(6).getGroups(), containsInAnyOrder("parent-seq", "t3"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("error-t1"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("t3"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.ERROR));
    }

    @Test
    void each() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/each-sequential-nested.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(7));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("1_each"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(1).getTask().getId(), is("1-1_return"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getId(), is("1_each"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getValue(), is("[\"s1\", \"s2\", \"s3\"]"));
        assertThat(flowTree.getTasks().get(1).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("1-2-1_return"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("1-2_each"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getValue(), is("[\"a a\", \"b b\"]"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(4).getTask().getId(), is("1-2-2_return"));
        assertThat(flowTree.getTasks().get(4).getParent().get(0).getId(), is("1-2-1_return"));
        assertThat(flowTree.getTasks().get(4).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(4).getGroups(), containsInAnyOrder("1_each", "1-2_each"));
    }

    @Test
    void allFlowable() throws IOException, IllegalVariableEvaluationException {
        Flow flow = this.parse("flows/valids/all-flowable.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(20));
    }

    @Test
    void eachWithExecution() throws IOException, TimeoutException, IllegalVariableEvaluationException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential");

        Flow flow = this.parse("flows/valids/each-sequential.yaml");
        FlowTree flowTree = FlowTree.of(flow, execution);

        assertThat(flowTree.getTasks().size(), is(8));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("1_each"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(2).getTask().getId(), is("1-1"));
        assertThat(flowTree.getTasks().get(2).getParent().get(0).getId(), is("1_each"));
        assertThat(flowTree.getTasks().get(2).getParent().get(0).getValue(), is("value 2"));
        assertThat(flowTree.getTasks().get(2).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("1-2"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("1-1"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getValue(), is("value 3"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(6).getGroups(), containsInAnyOrder("1_each"));
    }


    private Flow parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}
