package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.queues.QueueException;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringContains.containsString;

@KestraTest(startRunner = true)
public class EachSequentialTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Inject
    private RunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/each-sequential.yaml")
    void sequential(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
    }

    @Test
    @ExecuteFlow("flows/valids/each-object.yaml")
    void object(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat((String) execution.getTaskRunList().get(6).getOutputs().get("value"), containsString("json > JSON > [\"my-complex\"]"));
    }

    @Test
    @ExecuteFlow("flows/valids/each-object-in-list.yaml")
    void objectInList(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat((String) execution.getTaskRunList().get(6).getOutputs().get("value"), containsString("json > JSON > [\"my-complex\"]"));
    }

    @Test
    @ExecuteFlow("flows/valids/each-sequential-nested.yaml")
    void sequentialNested(Execution execution) throws InternalException {
        assertThat(execution.getTaskRunList(), hasSize(23));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        TaskRun last = execution.findTaskRunsByTaskId("2_return").getFirst();
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
    @ExecuteFlow("flows/valids/each-empty.yaml")
    void eachEmpty(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/each-null.yaml"})
    void eachNull() throws TimeoutException, QueueException {
        EachSequentialTest.eachNullTest(runnerUtils, logQueue);
    }

    public static void eachNullTest(RunnerUtils runnerUtils, QueueInterface<LogEntry> logQueue) throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-null", Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getMessage().contains("Found '1' null values on Each, with values=[1, null, {key=my-key, value=my-value}]"));
        receive.blockLast();
        assertThat(matchingLog, notNullValue());
    }

    @Test
    @ExecuteFlow("flows/valids/each-switch.yaml")
    void eachSwitch(Execution execution) throws InternalException {
        assertThat(execution.getTaskRunList(), hasSize(12));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        TaskRun switchNumber1 = execution.findTaskRunByTaskIdAndValue("2-1-1_switch-number-1", Arrays.asList("b", "1"));
        assertThat((String) switchNumber1.getOutputs().get("value"), is("1"));

        TaskRun switchNumber2 = execution.findTaskRunByTaskIdAndValue("2-1-1_switch-number-2", Arrays.asList("b", "2"));
        assertThat((String) switchNumber2.getOutputs().get("value"), is("2 b"));
    }

    @Test
    @ExecuteFlow("flows/valids/each-disabled-tasks.yaml")
    void eachDisabledTasks(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
