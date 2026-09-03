package io.kestra.plugin.scripts.exec.scripts.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperExecutionContextTest {

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
    private StorageInterface storageInterface;

    @Test
    void shouldWriteTheExecutionContextInTheWorkingDirectory() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // When — the script copies the context file out so its content can be asserted on
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withExecutionContext(true)
            .withOutputFiles(List.of("context.json"))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("cp " + CommandsWrapper.EXECUTION_CONTEXT_FILE_NAME + " context.json")))
            .run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);

        Map<String, Object> context = JacksonMapper.ofJson().readValue(
            storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("context.json")),
            JacksonMapper.MAP_TYPE_REFERENCE
        );

        assertThat(context).containsKeys("flow", "execution", "task", "taskrun");
        // envs and globals are already handed to the script as environment variables,
        // addSecretConsumer is internal plumbing that has no business in a metadata file
        assertThat(context).doesNotContainKeys("envs", "globals", "addSecretConsumer");
    }

    @Test
    void shouldNotWriteTheExecutionContextByDefault() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // When
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("test ! -f " + CommandsWrapper.EXECUTION_CONTEXT_FILE_NAME)))
            .run();

        // Then — the script itself asserts the file is absent
        assertThat(run.getExitCode()).isEqualTo(0);
    }
}
