package org.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueException;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.runners.*;
import org.kestra.core.tasks.flows.EachSequentialTest;
import org.kestra.core.tasks.flows.TemplateTest;
import org.kestra.core.utils.TestsUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaRunnerTest extends AbstractKafkaRunnerTest {
    @Inject
    private RestartCaseTest restartCaseTest;

    @Inject
    private FlowTriggerCaseTest flowTriggerCaseTest;

    @Inject
    private MultipleConditionTriggerCaseTest multipleConditionTriggerCaseTest;

    @Inject
    private TemplateRepositoryInterface templateRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    void full() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "full", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void logs() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "logs", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(3));
    }

    @Test
    void errors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "errors", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void sequential() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void parallel() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void parallelNested() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void eachSequentialNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(23));
    }

    @Test
    void eachParallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void eachParallelNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void listeners() throws TimeoutException, QueueException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests")));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "OK"),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ok"));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution-success-listener"));
    }

    @Test
    void recordTooLarge() {
        char[] chars = new char[11000000];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            runnerUtils.runOne(
                "org.kestra.tests",
                "inputs",
                null,
                (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
                Duration.ofSeconds(60)
            );
        });

        assertThat(e.getCause().getClass(), is(ExecutionException.class));
        assertThat(e.getCause().getCause().getClass(), is(RecordTooLargeException.class));
    }

    @Test
    void streamTooLarge() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(120)
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().contains("max.request.size")).count(), greaterThan(0L));
    }

    @Test
    void workerRecordTooLarge() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        char[] chars = new char[600000];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(logs.stream().filter(logEntry -> logEntry.getExecutionId().equals(execution.getId())).count(), is(131L));
    }

    @Test
    void invalidVars() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables-invalid", null, null, Duration.ofSeconds(60));

        List<LogEntry> filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(1));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(filters.get(0).getMessage(), containsString("Missing variable: inputs.invalid"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void restart() throws Exception {
        restartCaseTest.restart();
    }

    @Test
    void flowTrigger() throws Exception {
        flowTriggerCaseTest.trigger();
    }

    @Test
    void multipleConditionTrigger() throws Exception {
        multipleConditionTriggerCaseTest.trigger();
    }

    @Test
    void eachWithNull() throws Exception {
        EachSequentialTest.eachNullTest(runnerUtils, workerTaskLogQueue);
    }

    @Test
    void withTemplate() throws Exception {
        TemplateTest.withTemplate(runnerUtils, templateRepository);
    }
}
