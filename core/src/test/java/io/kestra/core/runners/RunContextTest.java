package io.kestra.core.runners;

import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Value("${kestra.encryption.secret-key}")
    private String secretKey;

    @Test
    void logs() throws TimeoutException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        LogEntry matchingLog;
        workerTaskLogQueue.receive(either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs");

        assertThat(execution.getTaskRunList(), hasSize(5));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(0).getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.TRACE));
        assertThat(matchingLog.getMessage(), is("first t1"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(1).getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.WARN));
        assertThat(matchingLog.getMessage(), is("second io.kestra.core.tasks.log.Log"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(2).getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.ERROR));
        assertThat(matchingLog.getMessage(), is("third logs"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(3).getId()));
        assertThat(matchingLog, nullValue());
    }

    @Test
    void inputsLarge() throws TimeoutException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.receive(either -> logs.add(either.getLeft()));

        char[] chars = new char[1024 * 11];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.SUCCESS));

        List<LogEntry> logEntries = TestsUtils.awaitLogs(logs, logEntry -> logEntry.getTaskRunId() != null && logEntry.getTaskRunId().equals(execution.getTaskRunList().get(1).getId()), count -> count > 1);
        logEntries.sort(Comparator.comparingLong(value -> value.getTimestamp().toEpochMilli()));

        assertThat(logEntries.get(0).getTimestamp().toEpochMilli() + 1, is(logEntries.get(1).getTimestamp().toEpochMilli()));
    }

    @Test
    void variables() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "return");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(
            ZonedDateTime.from(ZonedDateTime.parse((String) execution.getTaskRunList().get(0).getOutputs().get("value"))),
            ZonedDateTimeMatchers.within(10, ChronoUnit.SECONDS, ZonedDateTime.now())
        );
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("task-id"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("return"));
    }

    @Test
    void taskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/task-defaults.yaml")));
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
        assertThat(storageInterface.getAttributes(null, uri).getSize(), is(size + 1));
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

    @Test
    void fileExtension() {
        RunContext runContext = runContextFactory.of();

        assertThat(runContext.fileExtension(null), nullValue());
        assertThat(runContext.fileExtension(""), nullValue());
        assertThat(runContext.fileExtension("/file/hello"), nullValue());
        assertThat(runContext.fileExtension("/file/hello.txt"), is(".txt"));
    }

    @Test
    void resolve() {
        RunContext runContext = runContextFactory.of();
        String baseDir = runContext.tempDir().toString();

        Path path = runContext.resolve(Path.of("file.txt"));
        assertThat(path.toString(), is(baseDir + "/file.txt"));

        path = runContext.resolve(Path.of("subdir/file.txt"));
        assertThat(path.toString(), is(baseDir + "/subdir/file.txt"));

        path = runContext.resolve(null);
        assertThat(path.toString(), is(baseDir));

        assertThrows(IllegalArgumentException.class, () -> runContext.resolve(Path.of("/etc/passwd")));
        assertThrows(IllegalArgumentException.class, () -> runContext.resolve(Path.of("../../etc/passwd")));
        assertThrows(IllegalArgumentException.class, () -> runContext.resolve(Path.of("subdir/../../../etc/passwd")));
    }

    @Test
    void encrypt() throws GeneralSecurityException {
        // given
        RunContext runContext = runContextFactory.of();
        String plainText = "toto";

        String encrypted = runContext.encrypt(plainText);
        String decrypted = EncryptionService.decrypt(secretKey, encrypted);

        assertThat(encrypted, not(plainText));
        assertThat(decrypted, is(plainText));
    }

    @Test
    void encryptedStringOutput() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "encrypted-string");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));
        TaskRun hello = execution.findTaskRunsByTaskId("hello").get(0);
        Map<String, String> valueOutput = (Map<String, String>) hello.getOutputs().get("value");
        assertThat(valueOutput.size(), is(2));
        assertThat(valueOutput.get("type"), is(EncryptedString.TYPE));
        // the value is encrypted so it's not the plaintext value of the task property
        assertThat(valueOutput.get("value"), not("Hello World"));
        TaskRun returnTask = execution.findTaskRunsByTaskId("return").get(0);
        // the output is automatically decrypted so the return has the decrypted value of the hello task output
        assertThat(returnTask.getOutputs().get("value"), is("Hello World"));
    }

    @Test
    void withDefaultInput() throws IllegalVariableEvaluationException {
        Flow flow = Flow.builder().id("triggerWithDefaultInput").namespace("io.kestra.test").revision(1).inputs(List.of(StringInput.builder().id("test").type(Type.STRING).defaults("test").build())).build();
        Execution execution = Execution.builder().id(IdUtils.create()).flowId("triggerWithDefaultInput").namespace("io.kestra.test").state(new State()).build();

        RunContext runContext = runContextFactory.of(flow, execution);

        assertThat(runContext.render("{{inputs.test}}"), is("test"));
    }
}
