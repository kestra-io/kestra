package io.kestra.core.models.script;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public abstract class AbstractScriptRunnerTest {
    @Inject private RunContextFactory runContextFactory;

    @Test
    protected void run() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var commands = Mockito.mock(ScriptCommands.class);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("echo 'Hello World'")));
        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String s, Boolean aBoolean) {
            }
        });

        var scriptRunner = scriptRunner();
        var result = scriptRunner.run(runContext, commands, Collections.emptyList(), Collections.emptyList());
        assertThat(result, notNullValue());
        assertThat(result.getExitCode(), is(0));
    }

    @Test
    protected void fail() {
        var runContext = runContext(this.runContextFactory);
        var commands = Mockito.mock(ScriptCommands.class);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("return 1")));
        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String s, Boolean aBoolean) {
            }
        });

        var scriptRunner = scriptRunner();
        assertThrows(ScriptException.class, () -> scriptRunner.run(runContext, commands, Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    protected void inputAndOutputFiles() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var workingDirectory = runContext.tempDir();
        var outputDirectory = workingDirectory.resolve(IdUtils.create());
        outputDirectory.toFile().mkdirs();
        Map<String, Object> additionalVars = new HashMap<>(Map.of(
            "workingDir", workingDirectory.toAbsolutePath().toString(),
            "outputDir", outputDirectory.toString()
        ));
        var commands = Mockito.mock(ScriptCommands.class);
        Mockito.when(commands.getCommands()).thenReturn(ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(), List.of("cp {{workingDir}}/data.txt {{workingDir}}/out.txt")));
        Mockito.when(commands.getLogConsumer()).thenReturn(new AbstractLogConsumer() {
            @Override
            public void accept(String s, Boolean aBoolean) {
            }
        });
        Mockito.when(commands.getAdditionalVars()).thenReturn(additionalVars);
        workingDirectory.resolve("data.txt").toFile().createNewFile();

        var scriptRunner = scriptRunner();
        var result = scriptRunner.run(runContext, commands, List.of("data.txt"), List.of("out.txt"));
        assertThat(result, notNullValue());
        assertThat(result.getExitCode(), is(0));
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
        TaskRun taskRun = TaskRun.builder().id("taskrun").taskId("task").flowId("flow").namespace("namespace").executionId("execution")
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
}