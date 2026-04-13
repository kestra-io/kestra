package io.kestra.plugin.core.flow;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class BadExecutableTest {

    @Test
    @ExecuteFlow(value = "flows/valids/executable-fail.yml", tenantId = "badexecutable")
    void badExecutable(Execution execution) {
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
