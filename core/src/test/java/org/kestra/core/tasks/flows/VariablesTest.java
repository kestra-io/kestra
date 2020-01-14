package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class VariablesTest extends AbstractMemoryRunnerTest {
    @Test
    void recursiveVars() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.findTaskRunByTaskId("variable").getOutputs().get("return"), is("1 > 2 > 3"));
    }
}