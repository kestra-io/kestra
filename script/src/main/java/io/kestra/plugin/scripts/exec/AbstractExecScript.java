package io.kestra.plugin.scripts.exec;

import com.google.common.annotations.Beta;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractExecScript extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Builder.Default
    @Schema(
        title = "The task runner to use — by default, Kestra runs all scripts in `DOCKER`.",
        description = "Only used if the `taskRunner` property is not set"
    )
    @PluginProperty
    @NotNull
    protected RunnerType runner = RunnerType.DOCKER;

    @Schema(
        title = "The task runner to use.",
        description = "Task runners are provided by plugins, each have their own properties."
    )
    @PluginProperty
    @Beta
    @Valid
    protected TaskRunner taskRunner;

    @Schema(
        title = "A list of commands that will run before the `commands`, allowing to set up the environment e.g. `pip install -r requirements.txt`."
    )
    @PluginProperty(dynamic = true)
    protected List<String> beforeCommands;

    @Schema(
        title = "Additional environment variables for the current process."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the task state to `WARNING` if any `stdErr` is emitted."
    )
    @PluginProperty
    @NotNull
    protected Boolean warningOnStdErr = true;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use."
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected List<String> interpreter = List.of("/bin/sh", "-c");

    @Builder.Default
    @Schema(
        title = "Fail the task on the first command with a non-zero status.",
        description = "If set to `false` all commands will be executed one after the other. The final state of task execution is determined by the last command. Note that this property maybe be ignored if a non compatible interpreter is specified." +
            "\nYou can also disable it if your interpreter does not support the `set -e`option."
    )
    @PluginProperty
    protected Boolean failFast = true;

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private List<String> outputFiles;

    @Schema(
        title = "Whether to setup the output directory mechanism.",
        description = "Required to use the {{ outputDir }} expression. Note that it could increase the starting time. Deprecated, use the `outputFiles` property instead.",
        defaultValue = "false",
        deprecated = true
    )
    @Deprecated
    private Boolean outputDirectory;

    abstract public DockerOptions getDocker();

    @Schema(
        title = "The task runner container image, only used if the task runner is container-based."
    )
    @PluginProperty(dynamic = true)
    public abstract String getContainerImage();

    /**
     * Allow setting Docker options defaults values.
     * To make it work, it is advised to set the 'docker' field like:
     *
     * <pre>{@code
     *     @Schema(
     *         title = "Docker options when using the `DOCKER` runner",
     *         defaultValue = "{image=python, pullPolicy=ALWAYS}"
     *     )
     *     @PluginProperty
     *     @Builder.Default
     *     protected DockerOptions docker = DockerOptions.builder().build();
     * }</pre>
     */
    protected DockerOptions injectDefaults(@NotNull DockerOptions original) {
        return original;
    }

    protected CommandsWrapper commands(RunContext runContext) throws IllegalVariableEvaluationException {
        return new CommandsWrapper(runContext)
            .withEnv(this.getEnv())
            .withWarningOnStdErr(this.getWarningOnStdErr())
            .withRunnerType(this.taskRunner == null ? this.getRunner() : null)
            .withContainerImage(runContext.render(this.getContainerImage()))
            .withTaskRunner(this.taskRunner)
            .withDockerOptions(this.injectDefaults(getDocker()))
            .withNamespaceFiles(this.namespaceFiles)
            .withInputFiles(this.inputFiles)
            .withOutputFiles(this.outputFiles)
            .withEnableOutputDirectory(this.getOutputDirectory())
            .withTimeout(this.getTimeout());
    }

    protected List<String> getBeforeCommandsWithOptions() {
        return mayAddExitOnErrorCommands(this.beforeCommands);
    }

    protected List<String> mayAddExitOnErrorCommands(List<String> commands) {
        if (!failFast) {
            return commands;
        }

        if (commands == null || commands.isEmpty()) {
            return getExitOnErrorCommands();
        }

        ArrayList<String> newCommands = new ArrayList<>(commands.size() + 1);
        newCommands.addAll(getExitOnErrorCommands());
        newCommands.addAll(commands);
        return newCommands;
    }

    /**
     * Gets the list of additional commands to be used for defining interpreter errors handling.
     * @return   list of commands;
     */
    protected List<String> getExitOnErrorCommands() {
        // errexit option may be unsupported by non-shell interpreter.
        return List.of("set -e");
    }

    /** {@inheritDoc} **/
    @Override
    public void kill() {
        if (this.taskRunner != null) {
            this.taskRunner.kill();
        }
    }
}
