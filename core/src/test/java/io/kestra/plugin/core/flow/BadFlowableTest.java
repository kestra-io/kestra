package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class BadFlowableTest {

    @Test
    @ExecuteFlow("flows/valids/flowable-fail.yaml")
    void sequential(Execution execution) {
        assertThat("Task runs were: \n"+ JacksonMapper.log(execution.getTaskRunList()), execution.getTaskRunList().size(), greaterThanOrEqualTo(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @ExecuteFlow("flows/valids/flowable-with-parent-fail.yaml")
    void flowableWithParentFail(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
