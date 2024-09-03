package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class EmptyVariablesTest extends AbstractMemoryRunnerTest {

    @Inject
    private FlowInputOutput flowIO;

    @Test
    void emptyVariables() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "empty-variables",
            null,
            (flow, exec) -> flowIO.typedInputs(flow, exec, Map.of("emptyKey", "{ \"foo\": \"\" }", "emptySubObject", "{\"json\":{\"someEmptyObject\":{}}}"))
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
    }
}
