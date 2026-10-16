package io.kestra.core.models.tasks.runners.types;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.core.runner.Process;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessTest extends AbstractTaskRunnerTest {

    @Override
    protected TaskRunner<?> taskRunner() {
        return new Process();
    }

    @Test
    void shouldQualifyExitCode137WithoutClassifyingItAsOom() throws Exception {
        var runContext = runContext(this.runContextFactory);
        TaskCommands commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(
            Property.ofValue(
                ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("exit 137"))
            )
        );

        assertThatThrownBy(() -> taskRunner().run(runContext, commands, Collections.emptyList()))
            .isInstanceOf(TaskException.class)
            .hasMessage("Command terminated with exit code 137 (SIGKILL). This may indicate an out-of-memory kill, but the cause cannot be confirmed by the Process task runner. Check the worker's memory limits and system logs.");
    }

    @Test
    void shouldKeepGenericFailureForOtherExitCodes() throws Exception {
        var runContext = runContext(this.runContextFactory);
        TaskCommands commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(
            Property.ofValue(
                ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("exit 143"))
            )
        );

        assertThatThrownBy(() -> taskRunner().run(runContext, commands, Collections.emptyList()))
            .isInstanceOf(TaskException.class)
            .hasMessage("Command failed with exit code 143");
    }
}