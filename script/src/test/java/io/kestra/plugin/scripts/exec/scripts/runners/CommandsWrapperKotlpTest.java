package io.kestra.plugin.scripts.exec.scripts.runners;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskLogLineMatcher;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class CommandsWrapperKotlpTest {

    private static final Task TASK = new Task() {
        @Override
        public String getId() {
            return "test";
        }

        @Override
        public String getType() {
            return "test";
        }
    };

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private TaskLogLineMatcher matcher;

    @Test
    void shouldWrapCommandsWithKotlpWhenEnabled() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKotlp(new KotlpOptions(true, null, null))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        ScriptOutput run = wrapper.run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);

        // The APE binary is bootstrapped through sh and the wrapped command follows the '--' separator.
        List<String> finalCommands = runContext.render(wrapper.getCommands()).asList(String.class);
        assertThat(finalCommands.get(0)).isEqualTo("/bin/sh");
        assertThat(finalCommands.get(1)).endsWith("/" + KotlpUtils.BINARY_NAME);
        assertThat(finalCommands.get(2)).isEqualTo("--");
        assertThat(finalCommands.subList(3, finalCommands.size())).containsExactly("/bin/sh", "-c", "echo hello");

        Path binary = runContext.workingDir().path().resolve(KotlpUtils.BINARY_NAME);
        assertThat(binary).exists();
        assertThat(Files.isExecutable(binary)).isTrue();

        // End to end: the embedded binary frames its records as ::{"otlp":<json>}:: and the matcher
        // turns them into metrics on the wrapper's own run context. A binary spelling the key the
        // old way would leave this empty rather than fail.
        assertThat(runContext.metrics()).anySatisfy(metric ->
            assertThat(metric.getName()).isEqualTo("process.cpu.time"));
    }

    @Test
    void shouldPassLogDirOptionWhenConfigured() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKotlp(new KotlpOptions(true, "telemetry", null))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        ScriptOutput run = wrapper.run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);

        List<String> finalCommands = runContext.render(wrapper.getCommands()).asList(String.class);
        int logDirIndex = finalCommands.indexOf("--log-dir");
        assertThat(logDirIndex).isGreaterThan(0);
        assertThat(finalCommands.get(logDirIndex + 1)).isEqualTo("telemetry");
        assertThat(logDirIndex).isLessThan(finalCommands.indexOf("--"));

        // kotlp resolves the relative log dir against its cwd, the working directory, and writes bare OTLP NDJSON.
        Path logFile = runContext.workingDir().path().resolve("telemetry/log.ndjson");
        assertThat(logFile).exists();
        List<String> lines = Files.readAllLines(logFile).stream().filter(line -> !line.isBlank()).toList();
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            JsonNode record = JacksonMapper.ofJson().readTree(line);
            assertThat(record.isObject()).isTrue();
        }

        // With --log-dir the file is the only full copy: the console prints the child's output raw,
        // so nothing reaches Kestra until the caller ships the file back through the matcher.
        assertThat(runContext.metrics()).isEmpty();
        try (InputStream inputStream = Files.newInputStream(logFile)) {
            assertThat(matcher.parseOtlp(inputStream, runContext.logger(), runContext, Instant.now())).isNotEmpty();
        }
        assertThat(runContext.metrics()).anySatisfy(metric ->
            assertThat(metric.getName()).isEqualTo("process.cpu.time"));
    }

    @Test
    void shouldPassLogFlushIntervalOptionWhenConfigured() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKotlp(new KotlpOptions(true, "telemetry", Duration.ofSeconds(60)))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        ScriptOutput run = wrapper.run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);

        List<String> finalCommands = runContext.render(wrapper.getCommands()).asList(String.class);
        int logDirIndex = finalCommands.indexOf("--log-dir");
        int logFlushIndex = finalCommands.indexOf("--log-flush-interval");
        assertThat(logFlushIndex).isGreaterThan(logDirIndex);
        assertThat(finalCommands.get(logFlushIndex + 1)).isEqualTo("60");
        assertThat(logFlushIndex).isLessThan(finalCommands.indexOf("--"));
    }

    @Test
    void shouldThrowWhenLogFlushIntervalIsSetWithoutLogDir() {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKotlp(new KotlpOptions(true, null, Duration.ofSeconds(60)))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        // Then
        assertThatThrownBy(wrapper::run).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The kotlp 'logFlushInterval' option requires 'logDir' to be set.");
    }

    @Test
    void shouldNotWrapCommandsWhenKotlpDisabled() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        ScriptOutput run = wrapper.run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);
        List<String> finalCommands = runContext.render(wrapper.getCommands()).asList(String.class);
        assertThat(finalCommands).containsExactly("/bin/sh", "-c", "echo hello");
        assertThat(runContext.workingDir().path().resolve(KotlpUtils.BINARY_NAME)).doesNotExist();
    }

    @Test
    void shouldKeepKotlpOptionsWhenEnvIsSet() {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // When — withEnv re-invokes the all-args constructor and must thread the kotlp options through.
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKotlp(new KotlpOptions(true, "telemetry", Duration.ofSeconds(60)))
            .withEnv(Map.of("FOO", "bar"));

        // Then
        assertThat(wrapper.getKotlp().isEnabled()).isTrue();
        assertThat(wrapper.getKotlp().logDir()).isEqualTo("telemetry");
        assertThat(wrapper.getKotlp().logFlushInterval()).isEqualTo(Duration.ofSeconds(60));
    }
}
