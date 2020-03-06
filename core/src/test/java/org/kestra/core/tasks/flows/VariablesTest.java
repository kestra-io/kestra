package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class VariablesTest extends AbstractMemoryRunnerTest {
    static {
        System.setProperty("KESTRA_TEST1", "true");
        System.setProperty("KESTRA_TEST2", "Pass by env");
    }

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
        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables-invalid");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(1).getAttempts().get(0).getLogs().get(0).getMessage(), containsString("Missing variable: inputs.invalid"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
