package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.StringContains.containsString;

public class EachSequentialTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential");

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void sequentialNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential-nested");

        assertThat(execution.getTaskRunList(), hasSize(23));

        TaskRun last = execution.findTaskRunsByTaskId("2_return").get(0);
        TaskRun vars = execution.findTaskRunByTaskIdAndValue("1_2_1-return", Arrays.asList("s1", "a"));
        assertThat((String) last.getOutputs().get("value"), containsString((String) vars.getOutputs().get("value")));
    }
}
