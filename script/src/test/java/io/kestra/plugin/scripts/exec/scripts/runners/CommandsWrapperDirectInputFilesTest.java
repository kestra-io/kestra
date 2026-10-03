package io.kestra.plugin.scripts.exec.scripts.runners;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CommandsWrapperDirectInputFilesTest {
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
    void shouldMaterializeInputFilesLocallyWhenRunnerDoesNotSupportDirectInputFiles() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        RecordingRunner runner = RecordingRunner.builder().type(RecordingRunner.class.getName()).supportsDirect(false).build();

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInputFiles(Map.of("file.txt", "content"))
            .run();

        assertThat(runner.capturedDirectInputFiles).isEmpty();
        assertThat(Files.readString(runContext.workingDir().resolve(Path.of("file.txt")))).isEqualTo("content");
    }

    @Test
    void shouldSplitKestraUriInputFilesForOptedInRunner() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        URI storedUri = runContext.storage().putFile(new ByteArrayInputStream("payload".getBytes()), "source.txt");
        RecordingRunner runner = RecordingRunner.builder().type(RecordingRunner.class.getName()).supportsDirect(true).build();

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInputFiles(Map.of("data.txt", storedUri.toString()))
            .run();

        assertThat(runner.capturedDirectInputFiles).containsEntry("data.txt", storedUri);
        assertThat(Files.exists(runContext.workingDir().resolve(Path.of("data.txt")))).isFalse();
    }

    @Test
    void shouldKeepInlineContentLocalForOptedInRunner() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        RecordingRunner runner = RecordingRunner.builder().type(RecordingRunner.class.getName()).supportsDirect(true).build();

        new CommandsWrapper(runContext)
            .withTaskRunner(runner)
            .withInputFiles(Map.of("inline.txt", "content"))
            .run();

        assertThat(runner.capturedDirectInputFiles).isEmpty();
        assertThat(Files.readString(runContext.workingDir().resolve(Path.of("inline.txt")))).isEqualTo("content");
    }

    @SuperBuilder
    @NoArgsConstructor
    public static class RecordingRunner extends TaskRunner<TaskRunnerDetailResult> implements RemoteRunnerInterface {
        private boolean supportsDirect;

        private transient Map<String, URI> capturedDirectInputFiles;

        @Override
        public TaskRunnerResult<TaskRunnerDetailResult> run(RunContext runContext, TaskCommands taskCommands, List<String> filesToDownload) {
            this.capturedDirectInputFiles = taskCommands.getDirectInputFiles();
            return new TaskRunnerResult<>(0, new DefaultLogConsumer(runContext));
        }

        @Override
        public boolean supportsDirectInputFiles() {
            return supportsDirect;
        }

        @Override
        public Property<Boolean> getSyncWorkingDirectory() {
            return Property.ofValue(false);
        }
    }
}
