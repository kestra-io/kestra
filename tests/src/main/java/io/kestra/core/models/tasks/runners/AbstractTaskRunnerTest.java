package io.kestra.core.models.tasks.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public abstract class AbstractTaskRunnerTest {
    @Inject private RunContextFactory runContextFactory;
    @Inject private StorageInterface storage;

    @Test
    protected void run() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("echo 'Hello World'")));

        var taskRunner = taskRunner();
        var result = taskRunner.run(runContext, commands, Collections.emptyList());
        assertThat(result, notNullValue());
        assertThat(result.getExitCode(), is(0));
    }

    @Test
    protected void outputDirDisabled() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getEnableOutputDirectory()).thenReturn(false);
        Mockito.when(commands.outputDirectoryEnabled()).thenReturn(false);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("echo 'Hello World'")));

        var taskRunner = taskRunner();
        assertThat(taskRunner.additionalVars(runContext, commands).containsKey(ScriptService.VAR_OUTPUT_DIR), is(false));
        assertThat(taskRunner.env(runContext, commands).containsKey(ScriptService.ENV_OUTPUT_DIR), is(false));

        var result = taskRunner.run(runContext, commands, Collections.emptyList());
        assertThat(result, notNullValue());
        assertThat(result.getExitCode(), is(0));
    }

    @Test
    protected void fail() throws IOException {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("return 1")));

        var taskRunner = taskRunner();
        assertThrows(TaskException.class, () -> taskRunner.run(runContext, commands, Collections.emptyList()));
    }

    @Test
    protected void inputAndOutputFiles() throws Exception {
        RunContext runContext = runContext(this.runContextFactory, Map.of("internalStorageFile", "kestra://some/internalStorage.txt"));

        var commands = initScriptCommands(runContext);

        // Generate internal storage file
        FileUtils.writeStringToFile(Path.of("/tmp/unittest/internalStorage.txt").toFile(), "Hello from internal storage", StandardCharsets.UTF_8);

        // Generate input files
        FileUtils.writeStringToFile(runContext.workingDir().resolve(Path.of("hello.txt")).toFile(), "Hello World", StandardCharsets.UTF_8);

        DefaultLogConsumer defaultLogConsumer = new DefaultLogConsumer(runContext);
        // This is purely to showcase that no logs is sent as STDERR for now as CloudWatch doesn't seem to send such information.
        Map<String, Boolean> logsWithIsStdErr = new HashMap<>();

        TaskRunner<?> taskRunner = taskRunner();

        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String log, Boolean isStdErr) {
                logsWithIsStdErr.put(log, isStdErr);
                defaultLogConsumer.accept(log, isStdErr);
            }
        });

        String wdir = this.needsToSpecifyWorkingDirectory() ? "{{ workingDir }}/" : "";
        List<String> renderedCommands = ScriptService.replaceInternalStorage(
            runContext,
            taskRunner.additionalVars(runContext, commands),
            ScriptService.scriptCommands(List.of("/bin/sh", "-c"), null, List.of(
                "cat " + wdir + "{{internalStorageFile}} && echo",
                "cat " + wdir + "hello.txt && echo",
                "cat " + wdir + "hello.txt > " + wdir + "output.txt",
                "echo -n 'file from output dir' > {{outputDir}}/file.txt",
                "mkdir {{outputDir}}/nested",
                "echo -n 'nested file from output dir' > {{outputDir}}/nested/file.txt",
                "echo '::{\"outputs\":{\"logOutput\":\"Hello World\"}}::'"
            )),
            taskRunner instanceof RemoteRunnerInterface
        );
        Mockito.when(commands.getCommands()).thenReturn(renderedCommands);

        List<String> filesToDownload = List.of("output.txt");
        TaskRunnerResult<?> run = taskRunner.run(runContext, commands, filesToDownload);

        Map<String, URI> outputFiles = ScriptService.uploadOutputFiles(runContext, commands.getOutputDirectory());
        outputFiles.putAll(FilesService.outputFiles(runContext, filesToDownload));

        // Exit code for successful job
        assertThat(run.getExitCode(), is(0));

        Set<Map.Entry<String, Boolean>> logEntries = logsWithIsStdErr.entrySet();
        assertThat(logEntries.stream().filter(e -> e.getKey().contains("Hello from internal storage")).findFirst().orElseThrow().getValue(), is(false));
        assertThat(logEntries.stream().filter(e -> e.getKey().contains("Hello World")).findFirst().orElseThrow().getValue(), is(false));

        // Verify outputFiles
        assertThat(IOUtils.toString(storage.get(null, "unittest", outputFiles.get("output.txt")), StandardCharsets.UTF_8), is("Hello World"));
        assertThat(IOUtils.toString(storage.get(null, "unittest", outputFiles.get("file.txt")), StandardCharsets.UTF_8), is("file from output dir"));
        assertThat(IOUtils.toString(storage.get(null, "unittest", outputFiles.get("nested/file.txt")), StandardCharsets.UTF_8), is("nested file from output dir"));

        assertThat(defaultLogConsumer.getOutputs().get("logOutput"), is("Hello World"));
    }

    @Test
    protected void failWithInput() throws IOException {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(
            List.of("/bin/sh", "-c"),
            Collections.emptyList(),
            List.of("echo '::{\"outputs\":{\"logOutput\":\"Hello World\"}}::'", "return 1"))
        );

        var taskRunner = taskRunner();
        TaskException taskException = assertThrows(TaskException.class, () -> taskRunner.run(runContext, commands, Collections.emptyList()));
        assertThat(taskException.getLogConsumer().getOutputs().get("logOutput"), is("Hello World"));
    }

    protected RunContext runContext(RunContextFactory runContextFactory) {
        return this.runContext(runContextFactory, null);
    }

    protected RunContext runContext(RunContextFactory runContextFactory, Map<String, Object> additionalVars) {
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
        TaskRun taskRun = TaskRun.builder().id(IdUtils.create()).taskId("task").flowId("flow").namespace("namespace").executionId("execution")
            .state(new State().withState(State.Type.RUNNING))
            .build();
        Flow flow = Flow.builder().id("flow").namespace("namespace").revision(1)
            .tasks(List.of(task))
            .build();
        Execution execution = Execution.builder().flowId("flow").namespace("namespace").id("execution")
            .taskRunList(List.of(taskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        RunContext runContext = runContextFactory.of(flow, task, execution, taskRun);

        if (additionalVars == null) {
            return runContext;
        }

        Map<String, Object> mergedVars = new HashMap<>(runContext.getVariables());
        mergedVars.putAll(additionalVars);

        return runContextFactory.of(mergedVars);
    }

    protected abstract TaskRunner<?> taskRunner();

    protected String defaultImage() {
        return "ubuntu";
    }

    protected TaskCommands initScriptCommands(RunContext runContext) throws IOException {
        var commands = Mockito.mock(TaskCommands.class);
        Mockito.when(commands.getContainerImage()).thenReturn(defaultImage());
        Mockito.when(commands.getLogConsumer()).thenReturn(new DefaultLogConsumer(runContext));

        var workingDirectory = runContext.workingDir().path();
        Mockito.when(commands.getWorkingDirectory()).thenReturn(workingDirectory);

        var outputDirectory = workingDirectory.resolve(IdUtils.create());
        outputDirectory.toFile().mkdirs();
        Mockito.when(commands.getOutputDirectory()).thenReturn(outputDirectory);
        Mockito.when(commands.getAdditionalVars()).thenReturn(Collections.emptyMap());
        Mockito.when(commands.getEnableOutputDirectory()).thenReturn(true);
        Mockito.when(commands.outputDirectoryEnabled()).thenReturn(true);
        Mockito.when(commands.getTimeout()).thenReturn(null);
        Mockito.when(commands.relativeWorkingDirectoryFilesPaths(true)).thenCallRealMethod();

        return commands;
    }

    // If the runner supports working directory override, it's not needed as we can move the current working directory to the proper directory.
    protected boolean needsToSpecifyWorkingDirectory() {
        return false;
    }
}