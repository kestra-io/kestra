package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.utils.TestsUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class VariablesTest extends AbstractMemoryRunnerTest {
    static {
        System.setProperty("KESTRA_TEST1", "true");
        System.setProperty("KESTRA_TEST2", "Pass by env");
    }

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    void recursiveVars() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables");

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("variable").get(0).getOutputs().get("value"), is("1 > 2 > 3"));
        assertThat(execution.findTaskRunsByTaskId("env").get(0).getOutputs().get("value"), is("true Pass by env"));
        assertThat(execution.findTaskRunsByTaskId("global").get(0).getOutputs().get("value"), is("string 1 true"));
    }

    @Test
    void invalidVars() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables-invalid");

        List<LogEntry> filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(1));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(filters.get(0).getMessage(), containsString("Missing variable: inputs.invalid"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
