package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class VariablesTest {
    @Inject
    DispatchQueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    @FlakyTest(description = "Depends on ENV_TEST1/ENV_TEST2 env vars; @ExecuteFlow parameter resolution can fail with a closed connection pool under CI load")
    @ExecuteFlow("flows/valids/variables.yaml")
    @EnabledIfEnvironmentVariable(named = "ENV_TEST1", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "ENV_TEST2", matches = ".*")
    void recursiveVars(Execution execution) throws io.kestra.core.exceptions.InternalException {
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("variable").getFirst()).get("value")).isEqualTo("1 > 2 > 3");
        assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("env").getFirst()).get("value")).isEqualTo("true Pass by env");
        assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("global").getFirst()).get("value")).isEqualTo("string 1 true 2");
    }

    @Test
    @LoadFlows({ "flows/valids/variables-invalid.yaml" })
    void invalidVars() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.addListener(logs::add);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "variables-invalid");

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        LogEntry matchingLog = TestsUtils.awaitLog(
            logs, logEntry -> Objects.equals(logEntry.getTaskRunId(), execution.getTaskRunList().get(1).getId()) &&
                logEntry.getMessage().contains("Unable to find `inputs` used in the expression `{{inputs.invalid}}`")
        );
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
