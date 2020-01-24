package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class VariablesTest extends AbstractMemoryRunnerTest {
    static {
        System.setProperty("KESTRA_TEST1", "true");
        System.setProperty("KESTRA_TEST2", "Pass by env");
    }

    @Test
    void recursiveVars() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables");

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunByTaskId("variable").getOutputs().get("value"), is("1 > 2 > 3"));
        assertThat(execution.findTaskRunByTaskId("env").getOutputs().get("value"), is("true Pass by env"));
        assertThat(execution.findTaskRunByTaskId("global").getOutputs().get("value"), is("string 1 true"));
    }
}