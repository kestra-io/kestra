package io.kestra.plugin.scripts.exec.scripts.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkingDir;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperDurableTest {
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

    private static final List<String> SH = List.of("/bin/sh", "-c");

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void shouldExposeCheckpointsAsEnvironmentVariablesWhenDurable() throws Exception {
        // Given
        RunContext runContext = runContextWithCheckpoint();

        // When
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withDurable(true)
            .withInterpreter(Property.ofValue(SH))
            .withCommands(Property.ofValue(List.of("test \"$" + ScriptService.ENV_CHECKPOINTS_PREFIX + "LOOP\" = \"3\"")))
            .run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);
    }

    @Test
    void shouldExposeCheckpointWithShellSafeNameWhenKeyHasInvalidCharacters() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        runContext.checkpoints().set("my-cursor", "7");

        // When
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withDurable(true)
            .withInterpreter(Property.ofValue(SH))
            .withCommands(Property.ofValue(List.of("test \"$" + ScriptService.ENV_CHECKPOINTS_PREFIX + "MY_CURSOR\" = \"7\"")))
            .run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);
    }

    @Test
    void shouldNotExposeCheckpointsWhenNotDurable() throws Exception {
        // Given
        RunContext runContext = runContextWithCheckpoint();

        // When
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(SH))
            .withCommands(Property.ofValue(List.of("test -z \"$" + ScriptService.ENV_CHECKPOINTS_PREFIX + "LOOP\"")))
            .run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);
    }

    @Test
    void shouldWriteCheckpointsInTheExecutionContextWhenDurable() throws Exception {
        // Given
        RunContext runContext = runContextWithCheckpoint();

        // When
        ScriptOutput run = copyExecutionContext(new CommandsWrapper(runContext).withDurable(true));

        // Then
        assertThat(executionContext(run)).containsEntry(ScriptService.VAR_CHECKPOINTS, Map.of("loop", "3"));
    }

    @Test
    void shouldNotWriteCheckpointsInTheExecutionContextWhenNotDurable() throws Exception {
        // Given
        RunContext runContext = runContextWithCheckpoint();

        // When
        ScriptOutput run = copyExecutionContext(new CommandsWrapper(runContext));

        // Then
        assertThat(executionContext(run)).doesNotContainKey(ScriptService.VAR_CHECKPOINTS);
    }

    @Test
    void shouldEnableDurableFromTheTaskProperty() throws Exception {
        // Given
        RunContext runContext = runContextWithCheckpoint();
        DurableScript durable = DurableScript.builder().id("durable").type(DurableScript.class.getName()).taskRunner(Process.instance()).durable(Property.ofValue(true)).build();
        DurableScript notDurable = DurableScript.builder().id("not-durable").type(DurableScript.class.getName()).taskRunner(Process.instance()).build();

        // When
        ScriptOutput durableRun = durable.run(runContext, "test \"$" + ScriptService.ENV_CHECKPOINTS_PREFIX + "LOOP\" = \"3\"");
        ScriptOutput notDurableRun = notDurable.run(runContext, "test -z \"$" + ScriptService.ENV_CHECKPOINTS_PREFIX + "LOOP\"");

        // Then
        assertThat(durableRun.getExitCode()).isEqualTo(0);
        assertThat(notDurableRun.getExitCode()).isEqualTo(0);
    }

    private RunContext runContextWithCheckpoint() {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        runContext.checkpoints().set("loop", "3");
        return runContext;
    }

    private ScriptOutput copyExecutionContext(CommandsWrapper wrapper) throws Exception {
        return wrapper
            .withTaskRunner(Process.instance())
            .withExecutionContext(true)
            .withOutputFiles(List.of("context.json"))
            .withInterpreter(Property.ofValue(SH))
            .withCommands(Property.ofValue(List.of("cp " + WorkingDir.EXECUTION_CONTEXT_FILE_NAME + " context.json")))
            .run();
    }

    private Map<String, Object> executionContext(ScriptOutput run) throws Exception {
        return JacksonMapper.ofJson().readValue(
            storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("context.json")),
            JacksonMapper.MAP_TYPE_REFERENCE
        );
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    private static class DurableScript extends AbstractExecScript {
        private Property<String> containerImage;

        ScriptOutput run(RunContext runContext, String command) throws Exception {
            return commands(runContext)
                .withInterpreter(Property.ofValue(SH))
                .withCommands(Property.ofValue(List.of(command)))
                .run();
        }
    }
}
