package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class VariablesTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    @EnabledIfEnvironmentVariable(named = "KESTRA_TEST1", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "KESTRA_TEST2", matches = ".*")
    void recursiveVars() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "variables");

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("variable").getFirst().getOutputs().get("value"), is("1 > 2 > 3"));
        assertThat(execution.findTaskRunsByTaskId("env").getFirst().getOutputs().get("value"), is("true Pass by env"));
        assertThat(execution.findTaskRunsByTaskId("global").getFirst().getOutputs().get("value"), is("string 1 true 2"));
    }

    @Test
    void invalidVars() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "variables-invalid");


        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry ->
            Objects.equals(logEntry.getTaskRunId(), execution.getTaskRunList().get(1).getId()) &&
                logEntry.getMessage().contains("Unable to find `inputs` used in the expression `{{inputs.invalid}}`")
        );
        receive.blockLast();
        assertThat(matchingLog, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void failedFirst() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "failed-first");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }
}
