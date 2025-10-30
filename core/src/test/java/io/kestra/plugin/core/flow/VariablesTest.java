package io.kestra.plugin.core.flow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

@KestraTest(startRunner = true)
class VariablesTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/variables.yaml")
    @EnabledIfEnvironmentVariable(named = "ENV_TEST1", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "ENV_TEST2", matches = ".*")
    void recursiveVars(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.findTaskRunsByTaskId("variable").getFirst().getOutputs().get("value")).isEqualTo("1 > 2 > 3");
        assertThat(execution.findTaskRunsByTaskId("env").getFirst().getOutputs().get("value")).isEqualTo("true Pass by env");
        assertThat(execution.findTaskRunsByTaskId("global").getFirst().getOutputs().get("value")).isEqualTo("string 1 true 2");
    }

    @Test
    @LoadFlows({"flows/valids/variables-invalid.yaml"})
    void invalidVars() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "variables-invalid");


        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry ->
            Objects.equals(logEntry.getTaskRunId(), execution.getTaskRunList().get(1).getId()) &&
                logEntry.getMessage().contains("Unable to find `inputs` used in the expression `{{inputs.invalid}}`")
        );
        receive.blockLast();
        assertThat(matchingLog).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void failedFirst(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
