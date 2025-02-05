package io.kestra.plugin.scripts.exec;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.SystemUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractExecScript extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    @Schema(
        title = "Deprecated - use the 'taskRunner' property instead.",
        description = "Only used if the `taskRunner` property is not set",
        deprecated = true
    )
    @PluginProperty
    @Deprecated
    protected RunnerType runner;

    @Schema(
        title = "The task runner to use.",
        description = "Task runners are provided by plugins, each have their own properties."
    )
    @PluginProperty
    @Builder.Default
    @Valid
    protected TaskRunner<?> taskRunner = Docker.builder()
        .type(Docker.class.getName())
        .build();

    @Schema(
        title = "A list of commands that will run before the `commands`, allowing to set up the environment e.g. `pip install -r requirements.txt`."
    )
    protected Property<List<String>> beforeCommands;

    @Schema(
        title = "Additional environment variables for the current process."
    )
    protected Property<Map<String, String>> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the task state to `WARNING` when any `stdErr` output is detected.",
        description = "Note that a script error will set the state to `FAILED` regardless."
    )
    @NotNull
    protected Property<Boolean> warningOnStdErr = Property.of(true);

    @Builder.Default
    @Schema(
        title = "Which interpreter to use."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected Property<List<String>> interpreter = Property.of(List.of("/bin/sh", "-c"));

    @Builder.Default
    @Schema(
        title = "Fail the task on the first command with a non-zero status.",
        description = "If set to `false` all commands will be executed one after the other. The final state of task execution is determined by the last command. Note that this property maybe be ignored if a non compatible interpreter is specified." +
            "\nYou can also disable it if your interpreter does not support the `set -e`option."
    )
    protected Property<Boolean> failFast = Property.of(true);

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Schema(
        title = "Whether to setup the output directory mechanism.",
        description = "Required to use the {{ outputDir }} expression. Note that it could increase the starting time. Deprecated, use the `outputFiles` property instead.",
        defaultValue = "false",
        deprecated = true
    )
    @Deprecated
    private Property<Boolean> outputDirectory;

    @Schema(
        title = "The target operating system where the script will run."
    )
    @Builder.Default
    @NotNull
    protected Property<TargetOS> targetOS = Property.of(TargetOS.AUTO);

    @Schema(
        title = "Deprecated - use the 'taskRunner' property instead.",
        description = "Only used if the `taskRunner` property is not set",
        deprecated = true
    )
    @Deprecated
    protected DockerOptions docker;

    @Schema(
        title = "The task runner container image, only used if the task runner is container-based."
    )
    public abstract Property<String> getContainerImage();

    /**
     * @deprecated use {@link #injectDefaults(RunContext, DockerOptions)}
     */
    @Deprecated(forRemoval = true, since = "0.21")
    protected DockerOptions injectDefaults(@NotNull DockerOptions original) {
        return original;
    }

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
    protected DockerOptions injectDefaults(RunContext runContext, @NotNull DockerOptions original) throws IllegalVariableEvaluationException {
        // FIXME to keep backward compatibility, we call the old method from the new one by default
        return injectDefaults(original);
    }

    protected CommandsWrapper commands(RunContext runContext) throws IllegalVariableEvaluationException {
        if (this.getRunner() == null) {
            runContext.logger().debug("Using task runner '{}'", this.getTaskRunner().getType());
        }

        Map<String, String> renderedEnv = runContext.render(this.getEnv()).asMap(String.class, String.class);
        return new CommandsWrapper(runContext)
            .withEnv(renderedEnv.isEmpty() ? new HashMap<>() : renderedEnv)
            .withWarningOnStdErr(runContext.render(this.getWarningOnStdErr()).as(Boolean.class).orElseThrow())
            .withRunnerType(this.getRunner())
            .withContainerImage(runContext.render(this.getContainerImage()).as(String.class).orElse(null))
            .withTaskRunner(this.getTaskRunner())
            .withDockerOptions(this.getDocker() != null ? this.injectDefaults(runContext, this.getDocker()) : null)
            .withNamespaceFiles(this.getNamespaceFiles())
            .withInputFiles(this.getInputFiles())
            .withOutputFiles(runContext.render(this.getOutputFiles()).asList(String.class))
            .withEnableOutputDirectory(runContext.render(this.getOutputDirectory()).as(Boolean.class).orElse(null))
            .withTimeout(runContext.render(this.getTimeout()).as(Duration.class).orElse(null))
            .withTargetOS(runContext.render(this.getTargetOS()).as(TargetOS.class).orElseThrow());
    }

    protected List<String> getBeforeCommandsWithOptions(RunContext runContext) throws IllegalVariableEvaluationException {
        return mayAddExitOnErrorCommands(runContext.render(this.getBeforeCommands()).asList(String.class), runContext);
    }

    protected List<String> mayAddExitOnErrorCommands(List<String> commands, RunContext runContext) throws IllegalVariableEvaluationException {
        if (!runContext.render(this.getFailFast()).as(Boolean.class).orElseThrow()) {
            return commands;
        }

        if (commands == null || commands.isEmpty()) {
            return getExitOnErrorCommands(runContext);
        }

        ArrayList<String> newCommands = new ArrayList<>(commands.size() + 1);
        newCommands.addAll(getExitOnErrorCommands(runContext));
        newCommands.addAll(commands);
        return newCommands;
    }

    /**
     * Gets the list of additional commands to be used for defining interpreter errors handling.
     * @return   list of commands;
     */
    protected List<String> getExitOnErrorCommands(RunContext runContext) throws IllegalVariableEvaluationException {
        TargetOS rendered = runContext.render(this.getTargetOS()).as(TargetOS.class).orElseThrow();

        // If targetOS is Windows OR targetOS is AUTO && current system is windows and we use process as a runner.(TLDR will run on windows)
        if (rendered == TargetOS.WINDOWS ||
            (rendered == TargetOS.AUTO && SystemUtils.IS_OS_WINDOWS && this.getTaskRunner() instanceof Process)) {
            return List.of("");
        }
        // errexit option may be unsupported by non-shell interpreter.
        return List.of("set -e");
    }

    /** {@inheritDoc} **/
    @Override
    public void kill() {
        if (this.getTaskRunner() != null) {
            this.getTaskRunner().kill();
        }
    }
}
