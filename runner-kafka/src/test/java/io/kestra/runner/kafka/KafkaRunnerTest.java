package io.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.tasks.flows.EachSequentialTest;
import io.kestra.core.tasks.flows.FlowCaseTest;
import io.kestra.core.tasks.flows.TemplateTest;
import io.kestra.core.tasks.flows.WorkerTest;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "kestra.server-type", value = "EXECUTOR")
class KafkaRunnerTest extends AbstractKafkaRunnerTest {
    @Inject
    private RestartCaseTest restartCaseTest;

    @Inject
    private FlowTriggerCaseTest flowTriggerCaseTest;

    @Inject
    private MultipleConditionTriggerCaseTest multipleConditionTriggerCaseTest;

    @Inject
    private TaskDefaultsCaseTest taskDefaultsCaseTest;

    @Inject
    private TemplateRepositoryInterface templateRepository;

    @Inject
    private FlowCaseTest flowCaseTest;

    @Inject
    private WorkerTest.Suite workerTest;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logsQueue;

    @Test
    void full() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "full", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void logs() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "logs", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(3));
    }

    @Test
    void errors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "errors", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void sequential() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "sequential", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void parallel() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void parallelNested() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void eachSequentialNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-sequential-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(23));
    }

    @Test
    void eachParallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void eachParallelNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void listeners() throws TimeoutException, QueueException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests")));

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
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
                "io.kestra.tests",
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
        logsQueue.receive(logs::add);

        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
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
        logsQueue.receive(logs::add);

        char[] chars = new char[600000];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(logs.stream().filter(logEntry -> logEntry.getExecutionId().equals(execution.getId())).count(), is(greaterThan(60L)));
    }

    @Test
    void invalidVars() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        logsQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("io.kestra.tests", "variables-invalid", null, null, Duration.ofSeconds(60));

        List<LogEntry> filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(1));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(filters.stream().filter(logEntry -> logEntry.getMessage().contains("Missing variable: 'inputs' on '{{inputs.invalid}}'")).count(), greaterThan(0L));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void restartFailed() throws Exception {
        restartCaseTest.restartFailed();
    }

    @Test
    void replay() throws Exception {
        restartCaseTest.replay();
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
        EachSequentialTest.eachNullTest(runnerUtils, logsQueue);
    }

    @Test
    void withTemplate() throws Exception {
        TemplateTest.withTemplate(runnerUtils, templateRepository, repositoryLoader, logsQueue);
    }

    @Test
    void withFailedTemplate() throws Exception {
        TemplateTest.withFailedTemplate(runnerUtils, templateRepository, repositoryLoader, logsQueue);
    }

    @Test
    void taskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/task-defaults.yaml")));
        taskDefaultsCaseTest.taskDefaults();
    }

    @Test
    void invalidTaskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/invalid-task-defaults.yaml")));
        taskDefaultsCaseTest.invalidTaskDefaults();
    }

    @Test
    void flowWaitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    void flowWaitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    public void workerSuccess() throws Exception {
        workerTest.success(runnerUtils);
    }

    @Test
    public void workerFailed() throws Exception {
        workerTest.failed(runnerUtils);
    }

    @Test
    public void workerEach() throws Exception {
        workerTest.each(runnerUtils);
    }
}
