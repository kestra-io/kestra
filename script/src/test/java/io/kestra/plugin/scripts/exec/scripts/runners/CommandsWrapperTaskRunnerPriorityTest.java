package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkingDir;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.runner.docker.Docker;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandsWrapperTaskRunnerPriorityTest {

    private static RunContext mockRunContext() {
        RunContext runContext = mock(RunContext.class);
        WorkingDir workingDir = mock(WorkingDir.class);
        when(workingDir.path()).thenReturn(Paths.get("/tmp"));
        when(runContext.workingDir()).thenReturn(workingDir);
        return runContext;
    }

    @Test
    void shouldReturnNullWhenBothTaskRunnerAndRunnerTypeAreNull() {
        // Given
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext());

        // When
        TaskRunner<?> resolved = wrapper.getTaskRunner();

        // Then
        assertThat(resolved).isNull();
    }

    @Test
    void shouldUseLegacyRunnerTypeWhenTaskRunnerIsNull() {
        // Given — only legacy runnerType set (PROCESS), no taskRunner
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext())
            .withRunnerType(RunnerType.PROCESS);

        // When
        TaskRunner<?> resolved = wrapper.getTaskRunner();

        // Then
        assertThat(resolved).isInstanceOf(Process.class);
    }

    @Test
    void shouldUseExplicitTaskRunnerWhenRunnerTypeIsNull() {
        // Given — only taskRunner set, no legacy runnerType
        Docker docker = Docker.instance();
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext())
            .withTaskRunner(docker);

        // When
        TaskRunner<?> resolved = wrapper.getTaskRunner();

        // Then
        assertThat(resolved).isSameAs(docker);
    }

    @Test
    void shouldPreferExplicitTaskRunnerOverLegacyRunnerType() {
        // Given — both set: simulates a PluginDefault (non-forced) setting runner=PROCESS
        // while the flow explicitly sets taskRunner=Docker. User-explicit must win.
        Docker docker = Docker.instance();
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext())
            .withRunnerType(RunnerType.PROCESS)
            .withTaskRunner(docker);

        // When
        TaskRunner<?> resolved = wrapper.getTaskRunner();

        // Then
        assertThat(resolved).isSameAs(docker);
    }

    @Test
    void shouldNotEnableOutputDirectoryWhenTaskRunnerOverridesLegacyRunnerType() {
        // Given — bug scenario: legacy runnerType set (which historically enabled outputDirectory)
        // but explicit taskRunner overrides it. Output directory must mirror task runner precedence.
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext())
            .withRunnerType(RunnerType.PROCESS)
            .withTaskRunner(Docker.instance());

        // When / Then
        assertThat(wrapper.getEnableOutputDirectory()).isFalse();
    }

    @Test
    void shouldEnableOutputDirectoryWhenOnlyLegacyRunnerTypeIsSet() {
        // Given — legacy-only path: compat behavior must still enable output directory
        CommandsWrapper wrapper = new CommandsWrapper(mockRunContext())
            .withRunnerType(RunnerType.PROCESS);

        // When / Then
        assertThat(wrapper.getEnableOutputDirectory()).isTrue();
    }
}
