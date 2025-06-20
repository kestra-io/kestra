package io.kestra.core.runners;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class NullOutputTest {
    @Inject
    protected StandAloneRunner runner;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            runner.run();
        }
    }

    @Test
    @ExecuteFlow("flows/valids/null-output.yaml")
    void shouldIncludeNullOutput(Execution execution){
        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getOutputs().containsKey("value")).isTrue();
    }
}
