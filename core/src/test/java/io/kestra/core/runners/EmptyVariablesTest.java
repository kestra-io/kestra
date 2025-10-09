package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class EmptyVariablesTest {

    @Inject
    private TestRunnerUtils runnerUtils;
    @Inject
    private FlowInputOutput flowIO;

    @Test
    @LoadFlows({"flows/valids/empty-variables.yml"})
    void emptyVariables() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "empty-variables",
            null,
            (flow, exec) -> flowIO.readExecutionInputs(flow, exec, Map.of("emptyKey", "{ \"foo\": \"\" }", "emptySubObject", "{\"json\":{\"someEmptyObject\":{}}}"))
        );

        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);
    }
}
