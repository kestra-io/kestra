package io.kestra.plugin.scripts.exec.scripts.runners;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.runners.TaskRunnerResult;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperLogMarkerTest {
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
    void shouldEmitUnquotedLogMarkersForWindowsCmd() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        AtomicReference<List<String>> captured = new AtomicReference<>();

        TaskRunner<?> runner = Mockito.mock(TaskRunner.class);
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                TaskCommands taskCommands = invocation.getArgument(1);
                captured.set(runContext.render(taskCommands.getCommands()).asList(String.class));
                return TaskRunnerResult.builder().exitCode(0).build();
            });

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInterpreter(Property.ofValue(List.of("cmd.exe", "/c")))
            .withBeforeCommands(Property.ofValue(List.of("echo before")))
            .withCommands(Property.ofValue(List.of("echo main")))
            .run();

        assertThat(captured.get()).isNotNull();
        String script = captured.get().getLast();
        assertThat(script).contains("echo ##kestra:log:debug##");
        assertThat(script).contains("echo ##kestra:log:info##");
        assertThat(script).doesNotContain("echo '##kestra:log:debug##'");
        assertThat(script).doesNotContain("echo '##kestra:log:info##'");
    }

    @Test
    void shouldEmitUnquotedLogMarkersForWindowsCmdWithPath() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        AtomicReference<List<String>> captured = new AtomicReference<>();

        TaskRunner<?> runner = Mockito.mock(TaskRunner.class);
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                TaskCommands taskCommands = invocation.getArgument(1);
                captured.set(runContext.render(taskCommands.getCommands()).asList(String.class));
                return TaskRunnerResult.builder().exitCode(0).build();
            });

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInterpreter(Property.ofValue(List.of("C:\\Windows\\System32\\cmd.exe", "/c")))
            .withBeforeCommands(Property.ofValue(List.of("echo setup")))
            .withCommands(Property.ofValue(List.of("echo run")))
            .run();

        assertThat(captured.get()).isNotNull();
        String script = captured.get().getLast();
        assertThat(script).contains("echo ##kestra:log:debug##");
        assertThat(script).contains("echo ##kestra:log:info##");
    }

    @Test
    void shouldEmitQuotedLogMarkersForPowerShell() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        AtomicReference<List<String>> captured = new AtomicReference<>();

        TaskRunner<?> runner = Mockito.mock(TaskRunner.class);
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                TaskCommands taskCommands = invocation.getArgument(1);
                captured.set(runContext.render(taskCommands.getCommands()).asList(String.class));
                return TaskRunnerResult.builder().exitCode(0).build();
            });

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInterpreter(Property.ofValue(List.of("powershell.exe", "-Command")))
            .withBeforeCommands(Property.ofValue(List.of("Write-Output 'before'")))
            .withCommands(Property.ofValue(List.of("Write-Output 'main'")))
            .run();

        assertThat(captured.get()).isNotNull();
        String script = captured.get().getLast();
        assertThat(script).contains("echo '##kestra:log:debug##'");
        assertThat(script).contains("echo '##kestra:log:info##'");
    }

    @Test
    void shouldEmitQuotedLogMarkersForLinuxSh() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        AtomicReference<List<String>> captured = new AtomicReference<>();

        TaskRunner<?> runner = Mockito.mock(TaskRunner.class);
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(invocation -> {
                TaskCommands taskCommands = invocation.getArgument(1);
                captured.set(runContext.render(taskCommands.getCommands()).asList(String.class));
                return TaskRunnerResult.builder().exitCode(0).build();
            });

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withBeforeCommands(Property.ofValue(List.of("echo before")))
            .withCommands(Property.ofValue(List.of("echo main")))
            .run();

        assertThat(captured.get()).isNotNull();
        String script = captured.get().getLast();
        assertThat(script).contains("echo '##kestra:log:debug##'");
        assertThat(script).contains("echo '##kestra:log:info##'");
    }
}

