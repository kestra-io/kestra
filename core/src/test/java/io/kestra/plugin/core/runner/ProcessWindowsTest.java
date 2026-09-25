package io.kestra.plugin.core.runner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.Mockito;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractLogConsumer;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.tasks.runners.TaskRunnerResult;
import io.kestra.core.runners.RunContext;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@EnabledOnOs(OS.WINDOWS)
class ProcessWindowsTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldRunMultilineCmdCommandAndCaptureOutput() throws Exception {
        RunContext runContext = runContextFactory.of("flow", "company.team");
        List<String> lines = new ArrayList<>();
        TaskCommands commands = taskCommands(runContext, List.of("echo kestra-one", "echo kestra-two"), lines);

        TaskRunnerResult<?> result = new Process().run(runContext, commands, List.of());

        assertThat(result.getExitCode()).isZero();
        assertThat(lines).contains("kestra-one", "kestra-two");
        assertThat(runContext.workingDir().path().toFile().listFiles((dir, name) -> name.endsWith(".bat"))).isEmpty();
    }

    @Test
    void shouldPropagateNonZeroExitCode() {
        RunContext runContext = runContextFactory.of("flow", "company.team");
        TaskCommands commands = taskCommands(runContext, List.of("echo before-failure", "exit /b 3"), new ArrayList<>());

        assertThatThrownBy(() -> new Process().run(runContext, commands, List.of()))
            .isInstanceOf(TaskException.class)
            .satisfies(e -> assertThat(((TaskException) e).getExitCode()).isEqualTo(3));
    }

    private TaskCommands taskCommands(RunContext runContext, List<String> script, List<String> lines) {
        TaskCommands commands = Mockito.mock(TaskCommands.class);
        Mockito.when(commands.getCommands()).thenReturn(
            Property.ofValue(
                ScriptService.scriptCommands(List.of("cmd.exe", "/c"), List.of(), script, TargetOS.WINDOWS)
            )
        );
        Mockito.when(commands.getWorkingDirectory()).thenReturn(runContext.workingDir().path());
        Mockito.when(commands.getEnableOutputDirectory()).thenReturn(false);
        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String line, Boolean isStdErr, Instant instant) {
                lines.add(line);
            }

            @Override
            public void accept(String line, Boolean isStdErr) {
                lines.add(line);
            }
        });
        return commands;
    }
}
