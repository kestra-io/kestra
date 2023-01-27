package io.kestra.core.runners;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.annotation.Property;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.greaterThan;

@Property(name = "kestra.tasks.tmp-dir.path", value = "/tmp/sub/dir/tmp/")
class RunContextTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    TaskDefaultsCaseTest taskDefaultsCaseTest;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    MetricRegistry metricRegistry;

    @Test
    void logs() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        List<LogEntry> filters;
        workerTaskLogQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("io.kestra.tests", "logs");

        assertThat(execution.getTaskRunList(), hasSize(3));

        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(0));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.TRACE));
        assertThat(filters.get(0).getMessage(), is("first t1"));

        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(1));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.WARN));
        assertThat(filters.get(0).getMessage(), is("second io.kestra.core.tasks.debugs.Echo"));


        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(2));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.ERROR));
        assertThat(filters.get(0).getMessage(), is("third logs"));
    }

    @Test
    void inputsLarge() throws TimeoutException, InterruptedException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        char[] chars = new char[1024*11];
        Arrays.fill(chars, 'a');

        Map<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.SUCCESS));

        List<LogEntry> logEntries = logs.stream()
            .filter(logEntry -> logEntry.getTaskRunId() != null && logEntry.getTaskRunId().equals(execution.getTaskRunList().get(1).getId()))
            .sorted(Comparator.comparingLong(value -> value.getTimestamp().toEpochMilli()))
            .collect(Collectors.toList());

        Thread.sleep(100);
        assertThat(logEntries.get(0).getTimestamp().toEpochMilli() + 1, is(logEntries.get(1).getTimestamp().toEpochMilli()));
    }

    @Test
    void variables() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "return");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(
            ZonedDateTime.from(ZonedDateTime.parse((String) execution.getTaskRunList().get(0).getOutputs().get("value"))),
            ZonedDateTimeMatchers.within(10, ChronoUnit.SECONDS, ZonedDateTime.now())
        );
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("task-id"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("return"));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void metrics() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "return");

        TaskRunAttempt taskRunAttempt = execution.getTaskRunList()
            .get(1)
            .getAttempts()
            .get(0);
        Counter length = (Counter) taskRunAttempt.findMetrics("length").get();
        Timer duration = (Timer) taskRunAttempt.findMetrics("duration").get();

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(length.getValue(), is(7.0D));
        assertThat(duration.getValue().getNano(), is(greaterThan(0)));
        assertThat(duration.getTags().get("format"), is("{{task.id}}"));
    }

    @Test
    void taskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(RunContextTest.class.getClassLoader().getResource("flows/tests/task-defaults.yaml")));
        taskDefaultsCaseTest.taskDefaults();
    }

    @Test
    void tempFiles() throws IOException {
        RunContext runContext = runContextFactory.of();
        Path path = runContext.tempFile();

        assertThat(path.toFile().getAbsolutePath().startsWith("/tmp/sub/dir/tmp/"), is(true));
    }

    @Test
    void largeInput() throws IOException, InterruptedException {
        RunContext runContext = runContextFactory.of();
        Path path = runContext.tempFile();

        long size = 1024L * 1024 * 1024;

        Process p = Runtime.getRuntime().exec(String.format("dd if=/dev/zero of=%s bs=1 count=1 seek=%s", path, size));
        p.waitFor();
        p.destroy();

        URI uri = runContext.putTempFile(path.toFile());
        assertThat(storageInterface.size(uri), is(size+1));
    }

    @Test
    void invalidTaskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(new File(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/invalid-task-defaults.yaml")).toURI()));
        taskDefaultsCaseTest.invalidTaskDefaults();
    }

    @Test
    void metricsIncrement() {
        RunContext runContext = runContextFactory.of();

        Counter counter = Counter.of("counter", 12D);
        runContext.metric(counter);
        runContext.metric(Counter.of("counter", 30D));

        Timer timer = Timer.of("duration", Duration.ofSeconds(12));
        runContext.metric(timer);
        runContext.metric(Timer.of("duration", Duration.ofSeconds(30)));

        runContext.metric(Counter.of("counter", 123D, "key", "value"));
        runContext.metric(Timer.of("duration", Duration.ofSeconds(123), "key", "value"));

        assertThat(runContext.metrics().get(runContext.metrics().indexOf(counter)).getValue(), is(42D));
        assertThat(metricRegistry.counter("counter").count(), is(42D));
        assertThat(runContext.metrics().get(runContext.metrics().indexOf(timer)).getValue(), is(Duration.ofSeconds(42)));
        assertThat(metricRegistry.timer("duration").totalTime(TimeUnit.SECONDS), is(42D));

        assertThat(runContext.metrics().get(2).getValue(), is(123D));
        assertThat(runContext.metrics().get(2).getTags().size(), is(1));

        assertThat(runContext.metrics().get(3).getValue(), is(Duration.ofSeconds(123)));
        assertThat(runContext.metrics().get(3).getTags().size(), is(1));
    }
}
