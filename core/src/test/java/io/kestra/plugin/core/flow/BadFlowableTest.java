package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(execution.getTaskRunList().size()).as("Task runs were: \n" + JacksonMapper.log(execution.getTaskRunList())).isGreaterThanOrEqualTo(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/flowable-with-parent-fail.yaml")
    void flowableWithParentFail(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
