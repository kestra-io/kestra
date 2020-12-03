package org.kestra.core.tasks.flows;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.RunnerUtils;

import java.util.*;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;

public class EachSequentialTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential");

        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void sequentialNested() throws TimeoutException, InternalException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential-nested");

        assertThat(execution.getTaskRunList(), hasSize(23));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        TaskRun last = execution.findTaskRunsByTaskId("2_return").get(0);
        TaskRun lastWithValue = execution.findTaskRunByTaskIdAndValue("1-2-1_return", Arrays.asList("s1", "a a"));
        assertThat((String) last.getOutputs().get("value"), containsString((String) lastWithValue.getOutputs().get("value")));

        TaskRun evalL1 = execution.findTaskRunByTaskIdAndValue("1-3_return", Collections.singletonList("s1"));
        TaskRun evalL1Lookup = execution.findTaskRunByTaskIdAndValue("1-1_return", Collections.singletonList("s1"));
        assertThat((String) evalL1.getOutputs().get("value"), containsString((String) evalL1Lookup.getOutputs().get("value")));

        TaskRun evalL2 = execution.findTaskRunByTaskIdAndValue("1-2-2_return", Arrays.asList("s1", "a a"));
        TaskRun evalL2Lookup = execution.findTaskRunByTaskIdAndValue("1-2-1_return", Arrays.asList("s1", "a a"));
        assertThat((String) evalL2.getOutputs().get("value"), containsString("get " + (String) evalL2Lookup.getOutputs().get("value")));
        assertThat((String) evalL2.getOutputs().get("value"), containsString((String) evalL2Lookup.getOutputs().get("value")));
    }

    @Test
    void eachEmpty() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-empty");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void eachNull() throws TimeoutException {
        EachSequentialTest.eachNullTest(runnerUtils, logQueue);
    }

    public static void eachNullTest(RunnerUtils runnerUtils, QueueInterface<LogEntry> logQueue) throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-null");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(logs.get(1).getMessage(), Matchers.containsString("Found '1' null values on Each, with values=[1, null, 2]"));
    }

    @Test
    void eachSwitch() throws TimeoutException, InternalException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-switch");

        assertThat(execution.getTaskRunList(), hasSize(12));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        TaskRun switchNumber1 = execution.findTaskRunByTaskIdAndValue("2-1-1_switch-number-1", Arrays.asList("b", "1"));
        assertThat((String) switchNumber1.getOutputs().get("value"), is("1"));

        TaskRun switchNumber2 = execution.findTaskRunByTaskIdAndValue("2-1-1_switch-number-2", Arrays.asList("b", "2"));
        assertThat((String) switchNumber2.getOutputs().get("value"), is("2 b"));
    }
}
