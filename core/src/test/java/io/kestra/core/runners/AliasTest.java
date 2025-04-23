package io.kestra.core.runners;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class AliasTest {

    @Test
    @ExecuteFlow("flows/valids/alias-task.yaml")
    void taskAlias(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
    }

    @Test
    @ExecuteFlow("flows/valids/alias-trigger.yaml")
    void triggerAlias(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
    }
}
