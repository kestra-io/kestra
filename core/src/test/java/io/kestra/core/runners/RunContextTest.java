package io.kestra.core.runners;

import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
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
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

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

@Property(name = "kestra.tasks.tmp-dir.path", value = "/tmp/sub/dir/tmp/")
class RunContextTest extends AbstractMemoryRunnerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    PluginDefaultsCaseTest pluginDefaultsCaseTest;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    @Inject
    StorageInterface storageInterface;

    @Inject
    MetricRegistry metricRegistry;

    @Value("${kestra.encryption.secret-key}")
    private String secretKey;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    void logs() throws TimeoutException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        LogEntry matchingLog;
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs");

        assertThat(execution.getTaskRunList(), hasSize(5));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().getFirst().getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.TRACE));
        assertThat(matchingLog.getMessage(), is("first t1"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(1).getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.WARN));
        assertThat(matchingLog.getMessage(), is("second io.kestra.plugin.core.log.Log"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(2).getId()));
        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.ERROR));
        assertThat(matchingLog.getMessage(), is("third logs"));

        matchingLog = TestsUtils.awaitLog(logs, log -> Objects.equals(log.getTaskRunId(), execution.getTaskRunList().get(3).getId()));
        receive.blockLast();
        assertThat(matchingLog, nullValue());
    }

    @Test
    void inputsLarge() throws TimeoutException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        char[] chars = new char[1024 * 11];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> flowIO.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.SUCCESS));

        List<LogEntry> logEntries = TestsUtils.awaitLogs(logs, logEntry -> logEntry.getTaskRunId() != null && logEntry.getTaskRunId().equals(execution.getTaskRunList().get(1).getId()), count -> count > 1);
        receive.blockLast();
        logEntries.sort(Comparator.comparingLong(value -> value.getTimestamp().toEpochMilli()));

        assertThat(logEntries.getFirst().getTimestamp().toEpochMilli() + 1, is(logEntries.get(1).getTimestamp().toEpochMilli()));
    }

    @Test
    void variables() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "return");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(
            ZonedDateTime.from(ZonedDateTime.parse((String) execution.getTaskRunList().getFirst().getOutputs().get("value"))),
            ZonedDateTimeMatchers.within(10, ChronoUnit.SECONDS, ZonedDateTime.now())
        );
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("task-id"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("return"));
    }

    @Test
    void taskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/plugin-defaults.yaml")));
        pluginDefaultsCaseTest.taskDefaults();
    }

    @Test
    void largeInput() throws IOException, InterruptedException {
        RunContext runContext = runContextFactory.of();
        Path path = runContext.workingDir().createTempFile();

        long size = 1024L * 1024 * 1024;

        Process p = Runtime.getRuntime().exec(new String[] {"dd", "if=/dev/zero", String.format("of=%s", path), "bs=1", "count=1", String.format("seek=%s", size)});
        p.waitFor();
        p.destroy();

        URI uri = runContext.storage().putFile(path.toFile());
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
    void encrypt() throws GeneralSecurityException {
        // given
        RunContext runContext = runContextFactory.of();
        String plainText = "toto";

        String encrypted = runContext.encrypt(plainText);
        String decrypted = EncryptionService.decrypt(secretKey, encrypted);

        assertThat(encrypted, not(plainText));
        assertThat(decrypted, is(plainText));
    }

    @SuppressWarnings("unchecked")
    @Test
    void encryptedStringOutput() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "encrypted-string");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));
        TaskRun hello = execution.findTaskRunsByTaskId("hello").getFirst();
        Map<String, String> valueOutput = (Map<String, String>) hello.getOutputs().get("value");
        assertThat(valueOutput.size(), is(2));
        assertThat(valueOutput.get("type"), is(EncryptedString.TYPE));
        // the value is encrypted so it's not the plaintext value of the task property
        assertThat(valueOutput.get("value"), not("Hello World"));
        TaskRun returnTask = execution.findTaskRunsByTaskId("return").getFirst();
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

    @Test
    void withNullLabel() throws IllegalVariableEvaluationException {
        Flow flow = Flow.builder().id("triggerWithDefaultInput").namespace("io.kestra.test").revision(1).inputs(List.of(StringInput.builder().id("test").type(Type.STRING).defaults("test").build())).build();
        Execution execution = Execution.builder().id(IdUtils.create()).flowId("triggerWithDefaultInput").namespace("io.kestra.test").state(new State()).labels(List.of(new Label("key", null), new Label(null, "value"))).build();

        RunContext runContext = runContextFactory.of(flow, execution);

        assertThat(runContext.render("{{inputs.test}}"), is("test"));
    }

    @Test
    void renderMap() throws IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of(Map.of(
            "key", "default",
            "value", "default"
        ));

        Map<String, String> rendered = runContext.renderMap(Map.of("{{key}}", "{{value}}"));
        assertThat(rendered.get("default"), is("default"));

        rendered = runContext.renderMap(Map.of("{{key}}", "{{value}}"), Map.of(
            "key", "key",
            "value", "value"
        ));
        assertThat(rendered.get("key"), is("value"));
    }


    @Test
    void secretTrigger() throws IllegalVariableEvaluationException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        LogTrigger trigger = LogTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .format("john {{ secret('PASSWORD') }} doe")
            .build();

        Map.Entry<ConditionContext, Trigger> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

        WorkerTrigger workerTrigger = WorkerTrigger.builder()
            .trigger(trigger)
            .triggerContext(mockedTrigger.getValue())
            .conditionContext(mockedTrigger.getKey())
            .build();

        RunContext runContext = runContextInitializer.forWorker((DefaultRunContext) workerTrigger.getConditionContext().getRunContext(), workerTrigger);
        trigger.evaluate(mockedTrigger.getKey().withRunContext(runContext), mockedTrigger.getValue());

        matchingLog = TestsUtils.awaitLogs(logs, 3);
        receive.blockLast();
        assertThat(Objects.requireNonNull(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElse(null)).getMessage(), is("john ******** doe"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class LogTrigger extends AbstractTrigger implements PollingTriggerInterface {

        @PluginProperty
        @NotNull
        private String format;

        @Override
        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws IllegalVariableEvaluationException {
            conditionContext.getRunContext().logger().info(conditionContext.getRunContext().render(format));

            return Optional.empty();
        }

        @Override
        public Duration getInterval() {
            return null;
        }
    }
}
