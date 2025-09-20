package io.kestra.core.models.tasks.runners;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ScriptServiceTest {
    public static final Pattern COMMAND_PATTERN_CAPTURE_LOCAL_PATH = Pattern.compile("my command with an internal storage file: (.*)");
    @Inject private TestRunContextFactory runContextFactory;

    @Test
    void replaceInternalStorage() throws IOException {
        String tenant = IdUtils.create();
        var runContext = runContextFactory.of("id", "namespace", tenant);
        var command  = ScriptService.replaceInternalStorage(runContext, null, false);
        assertThat(command).isEqualTo("");

        command = ScriptService.replaceInternalStorage(runContext, "my command", false);
        assertThat(command).isEqualTo("my command");

        Path path = createFile(tenant, "file");

        String internalStorageUri = "kestra://some/file.txt";
        File localFile = null;
        try {
            command = ScriptService.replaceInternalStorage(runContext, "my command with an internal storage file: " + internalStorageUri, false);

            Matcher matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(command);
            assertThat(matcher.matches()).isTrue();
            Path absoluteLocalFilePath = Path.of(matcher.group(1));
            localFile = absoluteLocalFilePath.toFile();
            assertThat(localFile.exists()).isTrue();

            command = ScriptService.replaceInternalStorage(runContext, "my command with an internal storage file: " + internalStorageUri, true);
            matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(command);
            assertThat(matcher.matches()).isTrue();
            String relativePath = matcher.group(1);
            assertThat(relativePath).doesNotStartWith("/");
            assertThat(runContext.workingDir().resolve(Path.of(relativePath)).toFile().exists()).isTrue();
        } finally {
            localFile.delete();
            path.toFile().delete();
        }
    }

    @Test
    void replaceInternalStorageUnicode() throws IOException {
        String tenant = IdUtils.create();
        var runContext = runContextFactory.of("id", "namespace", tenant);

        Path path = createFile(tenant, "file-龍");

        String internalStorageUri = "kestra://some/file-龍.txt";
        File localFile = null;
        try {
            var command = ScriptService.replaceInternalStorage(runContext, "my command with an internal storage file: " + internalStorageUri, false);

            Matcher matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(command);
            assertThat(matcher.matches()).isTrue();
            Path absoluteLocalFilePath = Path.of(matcher.group(1));
            localFile = absoluteLocalFilePath.toFile();
            assertThat(localFile.exists()).isTrue();
        } finally {
            localFile.delete();
            path.toFile().delete();
        }
    }

    @Test
    void uploadInputFiles() throws IOException {
        String tenant = IdUtils.create();
        var runContext = runContextFactory.of("id", "namespace", tenant);

        Path path = createFile(tenant, "file");

        List<File> filesToDelete = new ArrayList<>();
        String internalStorageUri = "kestra://some/file.txt";

        try {
            String wdir = "/my/wd";
            var commands = ScriptService.replaceInternalStorage(
                runContext,
                Map.of("workingDir", wdir),
                List.of(
                    "my command with an internal storage file: " + internalStorageUri,
                    "my command with some additional var usage: {{ workingDir }}"
                ),
                false
            );
            assertThat(commands).isNotEmpty();

            assertThat(commands.getFirst(), not(is("my command with an internal storage file: " + internalStorageUri)));
            Matcher matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(commands.getFirst());
            assertThat(matcher.matches()).isTrue();
            File file = Path.of(matcher.group(1)).toFile();
            assertThat(file.exists()).isTrue();
            filesToDelete.add(file);

            assertThat(commands.get(1)).isEqualTo("my command with some additional var usage: " + wdir);

            commands = ScriptService.replaceInternalStorage(runContext, Collections.emptyMap(), List.of("my command with an internal storage file: " + internalStorageUri), true);
            matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(commands.getFirst());
            assertThat(matcher.matches()).isTrue();
            file = runContext.workingDir().resolve(Path.of(matcher.group(1))).toFile();
            assertThat(file.exists()).isTrue();
            filesToDelete.add(file);
        } catch (IllegalVariableEvaluationException e) {
            throw new RuntimeException(e);
        } finally {
            filesToDelete.forEach(File::delete);
            path.toFile().delete();
        }
    }

    @Test
    void uploadOutputFiles() throws IOException {
        String tenant = IdUtils.create();
        var runContext = runContextFactory.of("id", "namespace", tenant);
        Path path = createFile(tenant, "file");

        var outputFiles = ScriptService.uploadOutputFiles(runContext, Path.of("/tmp/unittest/%s".formatted(tenant)));
        assertThat(outputFiles, not(anEmptyMap()));
        assertThat(outputFiles.get("file.txt")).isEqualTo(URI.create("kestra:///file.txt"));

        path.toFile().delete();
    }

    @Test
    void scriptCommands() {
        var scriptCommands = ScriptService.scriptCommands(List.of("interpreter"), List.of("beforeCommand"), List.of("command"));
        assertThat(scriptCommands).hasSize(2);
        assertThat(scriptCommands.getFirst()).isEqualTo("interpreter");
        assertThat(scriptCommands.get(1)).isEqualTo("beforeCommand\ncommand");
    }

    @Test
    void labels() {
        var runContext = runContext(runContextFactory, "very.very.very.very.very.very.very.very.very.very.very.very.long.namespace");

        var labels = ScriptService.labels(runContext, "kestra.io/");
        assertThat(labels.size()).isEqualTo(6);
        assertThat(labels.get("kestra.io/namespace")).isEqualTo("very.very.very.very.very.very.very.very.very.very.very.very.lon");
        assertThat(labels.get("kestra.io/flow-id")).isEqualTo("flowId");
        assertThat(labels.get("kestra.io/task-id")).isEqualTo("task");
        assertThat(labels.get("kestra.io/execution-id")).isEqualTo("executionId");
        assertThat(labels.get("kestra.io/taskrun-id")).isEqualTo("taskrun");
        assertThat(labels.get("kestra.io/taskrun-attempt")).isEqualTo("0");

        labels = ScriptService.labels(runContext, null, true, true);
        assertThat(labels.size()).isEqualTo(6);
        assertThat(labels.get("namespace")).isEqualTo("very.very.very.very.very.very.very.very.very.very.very.very.lon");
        assertThat(labels.get("flow-id")).isEqualTo("flowid");
        assertThat(labels.get("task-id")).isEqualTo("task");
        assertThat(labels.get("execution-id")).isEqualTo("executionid");
        assertThat(labels.get("taskrun-id")).isEqualTo("taskrun");
        assertThat(labels.get("taskrun-attempt")).isEqualTo("0");
    }

    @Test
    void jobName() {
        var runContext = runContext(runContextFactory, "namespace");
        String jobName = ScriptService.jobName(runContext);
        assertThat(jobName).startsWith("namespace-flowid-task-");
        assertThat(jobName.length()).isEqualTo(27);

        runContext = runContext(runContextFactory, "very.very.very.very.very.very.very.very.very.very.very.very.long.namespace");
        jobName = ScriptService.jobName(runContext);
        assertThat(jobName).startsWith("veryveryveryveryveryveryveryveryveryveryveryverylongnames-");
        assertThat(jobName.length()).isEqualTo(63);
    }

    @Test
    void normalize() {
        assertThat(ScriptService.normalize(null)).isNull();
        assertThat(ScriptService.normalize("a-normal-string")).isEqualTo("a-normal-string");
        assertThat(ScriptService.normalize("very.very.very.very.very.very.very.very.very.very.very.very.long.namespace")).isEqualTo("very.very.very.very.very.very.very.very.very.very.very.very.lon");
    }

    private RunContext runContext(RunContextFactory runContextFactory, String namespace) {
        // create a fake flow and execution
        Task task = new Task() {
            @Override
            public String getId() {
                return "task";
            }

            @Override
            public String getType() {
                return "Task";
            }
        };
        TaskRun taskRun = TaskRun.builder().id("taskrun").taskId("task").flowId("flowId").namespace(namespace).executionId("executionId")
            .state(new State().withState(State.Type.RUNNING))
            .build();
        Flow flow = Flow.builder().id("flowId").namespace(namespace).revision(1)
            .tasks(List.of(task))
            .build();
        Execution execution = Execution.builder().flowId("flowId").namespace(namespace).id("executionId")
            .taskRunList(List.of(taskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();
        return runContextFactory.of(flow, task, execution, taskRun);
    }

    private static Path createFile(String tenant, String fileName) throws IOException {
        Path path = Path.of("/tmp/unittest/%s/%s.txt".formatted(tenant, fileName));
        if (!path.toFile().exists()) {
            Files.createDirectory(Path.of("/tmp/unittest/%s".formatted(tenant)));
            Files.createFile(path);
        }
        return path;
    }
}