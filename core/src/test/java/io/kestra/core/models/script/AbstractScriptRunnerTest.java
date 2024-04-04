package io.kestra.core.models.script;

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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public abstract class AbstractScriptRunnerTest {
    @Inject private RunContextFactory runContextFactory;
    @Inject private StorageInterface storage;

    @Test
    protected void run() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("echo 'Hello World'")));

        var scriptRunner = scriptRunner();
        var result = scriptRunner.run(runContext, commands, Collections.emptyList(), Collections.emptyList());
        assertThat(result, notNullValue());
        assertThat(result.getExitCode(), is(0));
    }

    @Test
    protected void fail() {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("return 1")));

        var scriptRunner = scriptRunner();
        assertThrows(ScriptException.class, () -> scriptRunner.run(runContext, commands, Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    protected void inputAndOutputFiles() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("internalStorageFile", "kestra://some/internalStorage.txt"));

        var commands = initScriptCommands(runContext);

        // Generate internal storage file
        FileUtils.writeStringToFile(Path.of("/tmp/unittest/internalStorage.txt").toFile(), "Hello from internal storage", StandardCharsets.UTF_8);

        // Generate input files
        FileUtils.writeStringToFile(runContext.resolve(Path.of("hello.txt")).toFile(), "Hello World", StandardCharsets.UTF_8);

        DefaultLogConsumer defaultLogConsumer = new DefaultLogConsumer(runContext);
        // This is purely to showcase that no logs is sent as STDERR for now as CloudWatch doesn't seem to send such information.
        Map<String, Boolean> logsWithIsStdErr = new HashMap<>();

        ScriptRunner scriptRunner = scriptRunner();

        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String log, Boolean isStdErr) {
                logsWithIsStdErr.put(log, isStdErr);
                defaultLogConsumer.accept(log, isStdErr);
            }
        });

        List<String> filesToUpload = new ArrayList<>();
        filesToUpload.add("hello.txt");

        String wdir = this.needsToSpecifyWorkingDirectory() ? "{{ workingDir }}/" : "";
        List<String> renderedCommands = ScriptService.replaceInternalStorage(
            runContext,
            scriptRunner.additionalVars(commands),
            ScriptService.scriptCommands(List.of("/bin/sh", "-c"), null, List.of(
                "cat " + wdir + "{{internalStorageFile}} && echo",
                "cat " + wdir + "hello.txt && echo",
                "cat " + wdir + "hello.txt > " + wdir + "output.txt",
                "echo -n 'file from output dir' > {{outputDir}}/file.txt",
                "mkdir {{outputDir}}/nested",
                "echo -n 'nested file from output dir' > {{outputDir}}/nested/file.txt",
                "echo '::{\"outputs\":{\"logOutput\":\"Hello World\"}}::'"
            )),
            (ignored, file) -> filesToUpload.add(file),
            scriptRunner instanceof RemoteRunnerInterface
        );
        Mockito.when(commands.getCommands()).thenReturn(renderedCommands);

        List<String> filesToDownload = List.of("output.txt");
        RunnerResult run = scriptRunner.run(runContext, commands, filesToUpload, filesToDownload);

        Map<String, URI> outputFiles = ScriptService.uploadOutputFiles(runContext, commands.getOutputDirectory());
        outputFiles.putAll(FilesService.outputFiles(runContext, filesToDownload));

        // Exit code for successful job
        assertThat(run.getExitCode(), is(0));

        Set<Map.Entry<String, Boolean>> logEntries = logsWithIsStdErr.entrySet();
        assertThat(logEntries.stream().filter(e -> e.getKey().contains("Hello from internal storage")).findFirst().orElseThrow().getValue(), is(false));
        assertThat(logEntries.stream().filter(e -> e.getKey().contains("Hello World")).findFirst().orElseThrow().getValue(), is(false));

        // Verify outputFiles
        assertThat(IOUtils.toString(storage.get(null, outputFiles.get("output.txt")), StandardCharsets.UTF_8), is("Hello World"));
        assertThat(IOUtils.toString(storage.get(null, outputFiles.get("file.txt")), StandardCharsets.UTF_8), is("file from output dir"));
        assertThat(IOUtils.toString(storage.get(null, outputFiles.get("nested/file.txt")), StandardCharsets.UTF_8), is("nested file from output dir"));

        assertThat(defaultLogConsumer.getOutputs().get("logOutput"), is("Hello World"));
    }

    protected RunContext runContext(RunContextFactory runContextFactory) {
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
        return runContextFactory.of(flow, task, execution, taskRun);
    }

    protected abstract ScriptRunner scriptRunner();

    protected String defaultImage() {
        return "ubuntu";
    }

    protected ScriptCommands initScriptCommands(RunContext runContext) {
        var commands = Mockito.mock(ScriptCommands.class);
        Mockito.when(commands.getContainerImage()).thenReturn(defaultImage());
        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String s, Boolean aBoolean) {
            }
        });

        var workingDirectory = runContext.tempDir();
        Mockito.when(commands.getWorkingDirectory()).thenReturn(workingDirectory);

        var outputDirectory = workingDirectory.resolve(IdUtils.create());
        outputDirectory.toFile().mkdirs();
        Mockito.when(commands.getOutputDirectory()).thenReturn(outputDirectory);
        Mockito.when(commands.getAdditionalVars()).thenReturn(new HashMap<>(Map.of(
            "workingDir", workingDirectory.toAbsolutePath().toString(),
            "outputDir", outputDirectory.toString()
        )));

        return commands;
    }

    // If the runner supports working directory override, it's not needed as we can move the current working directory to the proper directory.
    protected boolean needsToSpecifyWorkingDirectory() {
        return false;
    }
}