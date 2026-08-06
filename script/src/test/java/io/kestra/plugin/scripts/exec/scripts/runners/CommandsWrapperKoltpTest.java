package io.kestra.plugin.scripts.exec.scripts.runners;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperKoltpTest {

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

    @Test
    void shouldWrapCommandsWithKoltpWhenEnabled() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKoltp(new KoltpOptions(true, null))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello")));

        // When
        ScriptOutput run = wrapper.run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);

        // The APE binary is bootstrapped through sh and the wrapped command follows the '--' separator.
        List<String> finalCommands = runContext.render(wrapper.getCommands()).asList(String.class);
        assertThat(finalCommands.get(0)).isEqualTo("/bin/sh");
        assertThat(finalCommands.get(1)).endsWith("/" + KoltpUtils.BINARY_NAME);
        assertThat(finalCommands.get(2)).isEqualTo("--");
        assertThat(finalCommands.subList(3, finalCommands.size())).containsExactly("/bin/sh", "-c", "echo hello");

        Path binary = runContext.workingDir().path().resolve(KoltpUtils.BINARY_NAME);
        assertThat(binary).exists();
        assertThat(Files.isExecutable(binary)).isTrue();
    }

    @Test
    void shouldPassLogDirOptionWhenConfigured() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKoltp(new KoltpOptions(true, "telemetry"))
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

        // koltp resolves the relative log dir against its cwd, the working directory, and writes bare OTLP NDJSON.
        Path logFile = runContext.workingDir().path().resolve("telemetry/log.ndjson");
        assertThat(logFile).exists();
        List<String> lines = Files.readAllLines(logFile).stream().filter(line -> !line.isBlank()).toList();
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            JsonNode record = JacksonMapper.ofJson().readTree(line);
            assertThat(record.isObject()).isTrue();
        }
    }

    @Test
    void shouldNotWrapCommandsWhenKoltpDisabled() throws Exception {
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
        assertThat(runContext.workingDir().path().resolve(KoltpUtils.BINARY_NAME)).doesNotExist();
    }

    @Test
    void shouldKeepKoltpOptionsWhenEnvIsSet() {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // When — withEnv re-invokes the all-args constructor and must thread the koltp options through.
        CommandsWrapper wrapper = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withKoltp(new KoltpOptions(true, "telemetry"))
            .withEnv(Map.of("FOO", "bar"));

        // Then
        assertThat(wrapper.getKoltp().isEnabled()).isTrue();
        assertThat(wrapper.getKoltp().logDir()).isEqualTo("telemetry");
    }
}
