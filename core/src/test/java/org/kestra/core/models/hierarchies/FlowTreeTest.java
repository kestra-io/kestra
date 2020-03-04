package org.kestra.core.models.hierarchies;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.utils.TestsUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class FlowTreeTest extends AbstractMemoryRunnerTest {
    @Test
    void simple() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/return.yaml");
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
    void sequentialNested() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/sequential.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(11));

        assertThat(flowTree.getTasks().get(5).getTask().getId(), is("1-3-2.seq"));
        assertThat(flowTree.getTasks().get(5).getGroups(), containsInAnyOrder("1-3-2.seq", "1-3-seq", "1-seq"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("1-3-2.1"));
        assertThat(flowTree.getTasks().get(6).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("1-3-2.seq"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(7).getGroups(), containsInAnyOrder("1-3-2.seq", "1-3-seq", "1-seq"));

        assertThat(flowTree.getTasks().get(7).getTask().getId(), is("1-3-2.2.end"));
        assertThat(flowTree.getTasks().get(7).getParent().size(), is(1));
        assertThat(flowTree.getTasks().get(7).getParent().get(0).getId(), is("1-3-2.1"));
        assertThat(flowTree.getTasks().get(7).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(7).getGroups(), containsInAnyOrder("1-3-2.seq", "1-3-seq", "1-seq"));
    }

    @Test
    void errors() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/errors.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(7));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("2nd"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.ERROR));

        assertThat(flowTree.getTasks().get(1).getTask().getId(), is("3rd"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getId(), is("2nd"));
        assertThat(flowTree.getTasks().get(1).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(5).getTask().getId(), is("3rd - 1st - 1st - 1st - last"));
        assertThat(flowTree.getTasks().get(5).getGroups(), containsInAnyOrder("3rd", "3rd - 1st", "3rd - 1st - 1st", "3rd - 1st - 1st - 1st"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("failed"));
        assertThat(flowTree.getTasks().get(6).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
    }

    @Test
    void choice() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/switch.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(8));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("parent-seq"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("2nd"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("parent-seq"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.CHOICE));

        assertThat(flowTree.getTasks().get(4).getTask().getId(), is("2nd.sub"));
        assertThat(flowTree.getTasks().get(4).getParent().get(0).getId(), is("2nd"));
        assertThat(flowTree.getTasks().get(4).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("error-1st"));
        assertThat(flowTree.getTasks().get(6).getGroups(), containsInAnyOrder("parent-seq", "3th"));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("error-1st"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("3th"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.ERROR));
    }

    @Test
    void each() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/each-sequential-nested.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(7));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("1-each"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(1).getTask().getId(), is("1_1-return"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getId(), is("1-each"));
        assertThat(flowTree.getTasks().get(1).getParent().get(0).getValue(), is("[\"s1\", \"s2\", \"s3\"]"));
        assertThat(flowTree.getTasks().get(1).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(3).getTask().getId(), is("1_2_1-return"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getId(), is("1_2-each"));
        assertThat(flowTree.getTasks().get(3).getParent().get(0).getValue(), is("[\"a\", \"b\"]"));
        assertThat(flowTree.getTasks().get(3).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(4).getTask().getId(), is("1_2_2-return"));
        assertThat(flowTree.getTasks().get(4).getParent().get(0).getId(), is("1_2_1-return"));
        assertThat(flowTree.getTasks().get(4).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(4).getGroups(), containsInAnyOrder("1-each", "1_2-each"));
    }

    @Test
    void allFlowable() throws IOException {
        Flow flow = TestsUtils.parse("flows/valids/all-flowable.yaml");
        FlowTree flowTree = FlowTree.of(flow);

        assertThat(flowTree.getTasks().size(), is(20));
    }

    @Test
    void eachWithExecution() throws IOException, TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential");

        Flow flow = TestsUtils.parse("flows/valids/each-sequential.yaml");
        FlowTree flowTree = FlowTree.of(flow, execution);

        assertThat(flowTree.getTasks().size(), is(8));

        assertThat(flowTree.getTasks().get(0).getTask().getId(), is("1.each"));
        assertThat(flowTree.getTasks().get(0).getParent().size(), is(0));
        assertThat(flowTree.getTasks().get(0).getRelation(), is(RelationType.SEQUENTIAL));

        assertThat(flowTree.getTasks().get(2).getTask().getId(), is("1.each.1"));
        assertThat(flowTree.getTasks().get(2).getParent().get(0).getId(), is("1.each"));
        assertThat(flowTree.getTasks().get(2).getParent().get(0).getValue(), is("value 2"));
        assertThat(flowTree.getTasks().get(2).getRelation(), is(RelationType.DYNAMIC));

        assertThat(flowTree.getTasks().get(6).getTask().getId(), is("1.each.2"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getId(), is("1.each.1"));
        assertThat(flowTree.getTasks().get(6).getParent().get(0).getValue(), is("value 3"));
        assertThat(flowTree.getTasks().get(6).getRelation(), is(RelationType.SEQUENTIAL));
        assertThat(flowTree.getTasks().get(6).getGroups(), containsInAnyOrder("1.each"));
    }
}