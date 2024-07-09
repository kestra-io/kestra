package io.kestra.core.models.tasks.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ScriptServiceTest {
    public static final Pattern COMMAND_PATTERN_CAPTURE_LOCAL_PATH = Pattern.compile("my command with an internal storage file: (.*)");
    @Inject private RunContextFactory runContextFactory;

    @Test
    void replaceInternalStorage() throws IOException {
        var runContext = runContextFactory.of();
        var command  = ScriptService.replaceInternalStorage(runContext, null, false);
        assertThat(command, is(""));

        command = ScriptService.replaceInternalStorage(runContext, "my command", false);
        assertThat(command, is("my command"));

        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        String internalStorageUri = "kestra://some/file.txt";
        File localFile = null;
        try {
            command = ScriptService.replaceInternalStorage(runContext, "my command with an internal storage file: " + internalStorageUri, false);

            Matcher matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(command);
            assertThat(matcher.matches(), is(true));
            Path absoluteLocalFilePath = Path.of(matcher.group(1));
            localFile = absoluteLocalFilePath.toFile();
            assertThat(localFile.exists(), is(true));

            command = ScriptService.replaceInternalStorage(runContext, "my command with an internal storage file: " + internalStorageUri, true);
            matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(command);
            assertThat(matcher.matches(), is(true));
            String relativePath = matcher.group(1);
            assertThat(relativePath, not(startsWith("/")));
            assertThat(runContext.workingDir().resolve(Path.of(relativePath)).toFile().exists(), is(true));
        } finally {
            localFile.delete();
            path.toFile().delete();
        }
    }

    @Test
    void uploadInputFiles() throws IOException {
        var runContext = runContextFactory.of();

        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

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
            assertThat(commands, not(empty()));

            assertThat(commands.getFirst(), not(is("my command with an internal storage file: " + internalStorageUri)));
            Matcher matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(commands.getFirst());
            assertThat(matcher.matches(), is(true));
            File file = Path.of(matcher.group(1)).toFile();
            assertThat(file.exists(), is(true));
            filesToDelete.add(file);

            assertThat(commands.get(1), is("my command with some additional var usage: " + wdir));

            commands = ScriptService.replaceInternalStorage(runContext, Collections.emptyMap(), List.of("my command with an internal storage file: " + internalStorageUri), true);
            matcher = COMMAND_PATTERN_CAPTURE_LOCAL_PATH.matcher(commands.getFirst());
            assertThat(matcher.matches(), is(true));
            file = runContext.workingDir().resolve(Path.of(matcher.group(1))).toFile();
            assertThat(file.exists(), is(true));
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
        var runContext = runContextFactory.of();
        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        var outputFiles = ScriptService.uploadOutputFiles(runContext, Path.of("/tmp/unittest"));
        assertThat(outputFiles, not(anEmptyMap()));
        assertThat(outputFiles.get("file.txt"), is(URI.create("kestra:///file.txt")));

        path.toFile().delete();
    }

    @Test
    void scriptCommands() {
        var scriptCommands = ScriptService.scriptCommands(List.of("interpreter"), List.of("beforeCommand"), List.of("command"));
        assertThat(scriptCommands, hasSize(2));
        assertThat(scriptCommands.getFirst(), is("interpreter"));
        assertThat(scriptCommands.get(1), is("beforeCommand\ncommand"));
    }

    @Test
    void labels() {
        var runContext = runContext(runContextFactory, "very.very.very.very.very.very.very.very.very.very.very.very.long.namespace");

        var labels = ScriptService.labels(runContext, "kestra.io/");
        assertThat(labels.size(), is(6));
        assertThat(labels.get("kestra.io/namespace"), is("very.very.very.very.very.very.very.very.very.very.very.very.lon"));
        assertThat(labels.get("kestra.io/flow-id"), is("flowId"));
        assertThat(labels.get("kestra.io/task-id"), is("task"));
        assertThat(labels.get("kestra.io/execution-id"), is("executionId"));
        assertThat(labels.get("kestra.io/taskrun-id"), is("taskrun"));
        assertThat(labels.get("kestra.io/taskrun-attempt"), is("0"));

        labels = ScriptService.labels(runContext, null, true, true);
        assertThat(labels.size(), is(6));
        assertThat(labels.get("namespace"), is("very.very.very.very.very.very.very.very.very.very.very.very.lon"));
        assertThat(labels.get("flow-id"), is("flowid"));
        assertThat(labels.get("task-id"), is("task"));
        assertThat(labels.get("execution-id"), is("executionid"));
        assertThat(labels.get("taskrun-id"), is("taskrun"));
        assertThat(labels.get("taskrun-attempt"), is("0"));
    }

    @Test
    void jobName() {
        var runContext = runContext(runContextFactory, "namespace");
        String jobName = ScriptService.jobName(runContext);
        assertThat(jobName, startsWith("namespace-flowid-task-"));
        assertThat(jobName.length(), is(27));

        runContext = runContext(runContextFactory, "very.very.very.very.very.very.very.very.very.very.very.very.long.namespace");
        jobName = ScriptService.jobName(runContext);
        assertThat(jobName, startsWith("veryveryveryveryveryveryveryveryveryveryveryverylongnames-"));
        assertThat(jobName.length(), is(63));
    }

    @Test
    void normalize() {
        assertThat(ScriptService.normalize(null), nullValue());
        assertThat(ScriptService.normalize("a-normal-string"), is("a-normal-string"));
        assertThat(ScriptService.normalize("very.very.very.very.very.very.very.very.very.very.very.very.long.namespace"), is("very.very.very.very.very.very.very.very.very.very.very.very.lon"));
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
}