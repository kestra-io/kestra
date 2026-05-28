package io.kestra.plugin.scripts.exec;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import jakarta.inject.Inject;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AbstractExecScriptTaskRunnerPriorityTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @SuperBuilder
    static class TestScript extends AbstractExecScript {
        @Override
        public Property<String> getContainerImage() {
            return null;
        }
    }

    private RunContext mockRunContext(Task task) {
        return TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
    }

    @Test
    void shouldFallBackToDefaultDockerWhenNeitherTaskRunnerNorRunnerIsSet() throws Exception {
        // Given — historical default path: neither property set
        TestScript script = TestScript.builder().id("t").type("t").build();

        // When
        CommandsWrapper wrapper = script.commands(mockRunContext(script));

        // Then
        assertThat(wrapper.getTaskRunner()).isInstanceOf(Docker.class);
    }

    @Test
    void shouldUseLegacyRunnerWhenOnlyRunnerIsSet() throws Exception {
        // Given — legacy YAML: only deprecated `runner` set
        TestScript script = TestScript.builder().id("t").type("t").runner(RunnerType.PROCESS).build();

        // When
        CommandsWrapper wrapper = script.commands(mockRunContext(script));

        // Then — no default Docker injection; legacy path resolves via CommandsWrapper
        assertThat(wrapper.getTaskRunner()).isInstanceOf(Process.class);
    }

    @Test
    void shouldUseTaskRunnerWhenOnlyTaskRunnerIsSet() throws Exception {
        // Given — modern YAML: only `taskRunner` set
        Docker docker = Docker.instance();
        TestScript script = TestScript.builder().id("t").type("t").taskRunner(docker).build();

        // When
        CommandsWrapper wrapper = script.commands(mockRunContext(script));

        // Then
        assertThat(wrapper.getTaskRunner()).isSameAs(docker);
    }

    @Test
    void shouldPreferExplicitTaskRunnerWhenBothAreSet() throws Exception {
        // Given — bug scenario: PluginDefault sets runner=PROCESS, flow sets taskRunner=Docker.
        // Merged task has both fields populated; explicit taskRunner must win.
        Docker docker = Docker.instance();
        TestScript script = TestScript.builder()
            .id("t")
            .type("t")
            .runner(RunnerType.PROCESS)
            .taskRunner(docker)
            .build();

        // When
        CommandsWrapper wrapper = script.commands(mockRunContext(script));

        // Then
        assertThat(wrapper.getTaskRunner()).isSameAs(docker);
    }
}
