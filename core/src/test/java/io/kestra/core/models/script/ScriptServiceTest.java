package io.kestra.core.models.script;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class ScriptServiceTest {
    @Inject private RunContextFactory runContextFactory;

    @Test
    void replaceInternalStorage() throws IOException {
        var runContext = runContextFactory.of();
        var command  = ScriptService.replaceInternalStorage(runContext, null);
        assertThat(command, is(""));

        command = ScriptService.replaceInternalStorage(runContext, "my command");
        assertThat(command, is("my command"));

        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        String internalStorageUri = "kestra://some/file.txt";
        AtomicReference<String> localFile = new AtomicReference<>();
        try {
            command = ScriptService.replaceInternalStorage(runContext, "my command with a file: " + internalStorageUri, (ignored, file) -> localFile.set(file));
            assertThat(command, is("my command with a file: " + localFile.get()));
            assertThat(Path.of(localFile.get()).toFile().exists(), is(true));

            command = ScriptService.replaceInternalStorage(runContext, "my command with a file: " + internalStorageUri, (ignored, file) -> localFile.set(file), true);
            assertThat(command, is("my command with a file: " + localFile.get().substring(1)));
        } finally {
            Path.of(localFile.get()).toFile().delete();
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

        Map<String, String> localFileByInternalStorage = new HashMap<>();
        String internalStorageUri = "kestra://some/file.txt";
        try {
            var commands = ScriptService.uploadInputFiles(runContext, List.of("my command with a file: " + internalStorageUri), localFileByInternalStorage::put);
            assertThat(commands, not(empty()));
            assertThat(commands.get(0), is("my command with a file: " + localFileByInternalStorage.get(internalStorageUri)));
            assertThat(Path.of(localFileByInternalStorage.get(internalStorageUri)).toFile().exists(), is(true));
        } finally {
            localFileByInternalStorage.forEach((k, v) -> Path.of(v).toFile().delete());
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
        assertThat(scriptCommands.get(0), is("interpreter"));
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