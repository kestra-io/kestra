package io.kestra.plugin.scripts.exec.scripts.runners;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.DefaultLogConsumer;
import io.kestra.core.models.tasks.runners.RemoteRunnerInterface;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.runners.TaskRunnerDetailResult;
import io.kestra.core.models.tasks.runners.TaskRunnerResult;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperRemoteOutputFilesTest {
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
    void shouldUseOutputFilesAlreadyCollectedByTheRunner() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        URI remoteUri = runContext.storage().putFile(new java.io.ByteArrayInputStream("remote".getBytes()), "remote.txt");
        StubRunner runner = StubRunner.builder()
            .type(StubRunner.class.getName())
            .remoteOutputFiles(Map.of("remote.txt", remoteUri))
            .build();

        ScriptOutput output = new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .run();

        assertThat(output.getOutputFiles()).containsEntry("remote.txt", remoteUri);
    }

    @Test
    void shouldNotOverwriteRunnerCollectedOutputFileWithLocalCollectionForTheSameKey() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        URI remoteUri = runContext.storage().putFile(new java.io.ByteArrayInputStream("remote".getBytes()), "remote.txt");
        runContext.workingDir().createFile("result.txt", "local".getBytes());
        StubRunner runner = StubRunner.builder()
            .type(StubRunner.class.getName())
            .remoteOutputFiles(Map.of("result.txt", remoteUri))
            .build();

        ScriptOutput output = new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withOutputFiles(List.of("result.txt"))
            .run();

        // the runner already reported this key: the local file with the same name must not overwrite it
        assertThat(output.getOutputFiles()).containsEntry("result.txt", remoteUri);
    }

    @Test
    void shouldStillCollectLocalOutputFilesWhenTheRunnerDidNotPopulateAny() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        runContext.workingDir().createFile("result.txt", "local".getBytes());
        StubRunner runner = StubRunner.builder().type(StubRunner.class.getName()).build();

        ScriptOutput output = new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withOutputFiles(List.of("result.txt"))
            .run();

        assertThat(output.getOutputFiles()).containsKey("result.txt");
    }

    @SuperBuilder
    @NoArgsConstructor
    public static class StubRunner extends TaskRunner<TaskRunnerDetailResult> implements RemoteRunnerInterface {
        private Map<String, URI> remoteOutputFiles;

        @Override
        public TaskRunnerResult<TaskRunnerDetailResult> run(RunContext runContext, TaskCommands taskCommands, List<String> filesToDownload) {
            return new TaskRunnerResult<>(0, new DefaultLogConsumer(runContext), null, remoteOutputFiles);
        }

        @Override
        public Property<Boolean> getSyncWorkingDirectory() {
            return Property.ofValue(false);
        }
    }
}
