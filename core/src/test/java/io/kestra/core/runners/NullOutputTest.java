package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class NullOutputTest {
    @Inject
    protected StandAloneRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            runner.run();
        }
    }

    @Test
    @LoadFlows("flows/valids/null-output.yaml")
    void shouldIncludeNullOutput() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "null-output");

        assertThat(execution, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs(), aMapWithSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().containsKey("value"), is(true));
    }
}
