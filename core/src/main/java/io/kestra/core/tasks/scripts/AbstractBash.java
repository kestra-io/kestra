package io.kestra.core.tasks.scripts;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.runners.DockerScriptRunner;
import io.kestra.core.tasks.scripts.runners.ProcessBuilderScriptRunner;
import io.kestra.core.tasks.scripts.runners.ScriptRunnerInterface;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class AbstractBash extends Task {
    @Builder.Default
    @Schema(
        title = "Runner to use"
    )
    @PluginProperty(dynamic = false)
    @NotNull
    @NotEmpty
    protected AbstractBash.Runner runner = Runner.PROCESS;

    @Schema(
        title = "Docker options when using runner `DOCKER`"
    )
    protected DockerOptions dockerOptions;

    @Builder.Default
    @Schema(
        title = "Interpreter to used"
    )
    @PluginProperty(dynamic = false)
    @NotNull
    @NotEmpty
    protected String interpreter = "/bin/sh";

    @Builder.Default
    @Schema(
        title = "Interpreter args used"
    )
    @PluginProperty(dynamic = false)
    protected String[] interpreterArgs = {"-c"};

    @Builder.Default
    @Schema(
        title = "Exit if any non true return value",
        description = "This tells bash that it should exit the script if any statement returns a non-true return value. \n" +
            "The benefit of using -e is that it prevents errors snowballing into serious issues when they could " +
            "have been caught earlier."
    )
    @PluginProperty(dynamic = false)
    @NotNull
    protected Boolean exitOnFailed = true;

    @Schema(
        title = "The list of files that will be uploaded to internal storage, ",
        description ="use `outputFiles` property instead",
        deprecated = true
    )
    @PluginProperty(dynamic = true)
    @Deprecated
    protected List<String> files;

    @Schema(
        title = "Deprecated Output file",
        description = "use `outputFiles`",
        deprecated = true
    )
    @PluginProperty(dynamic = false)
    @Deprecated
    protected List<String> outputsFiles;

    @Schema(
        title = "Output file list that will be uploaded to internal storage",
        description = "List of key that will generate temporary files.\n" +
            "On the command, just can use with special variable named `outputFiles.key`.\n" +
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ outputFiles.first }}`" +
            " and you used on others tasks using `{{ outputs.task-id.files.first }}`"
    )
    @PluginProperty(dynamic = false)
    protected List<String> outputFiles;

    @Schema(
        title = "Input files are extra files supplied by user that make it simpler organize code.",
        description = "Describe a files map that will be written and usable in execution context. In python execution " +
            "context is in a temp folder, for bash scripts, you can reach files using a workingDir variable " +
            "like 'source {{workingDir}}/myfile.sh' "
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> inputFiles;

    @Schema(
        title = "Additional environnements variable to add for current process."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Builder.Default
    @Schema(
        title = "Use `WARNING` state if any stdErr is sent"
    )
    @PluginProperty(dynamic = false)
    @NotNull
    protected Boolean warningOnStdErr = true;

    @Getter(AccessLevel.NONE)
    protected transient Path workingDirectory;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected transient Map<String, Object> additionalVars = new HashMap<>();

    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        return this.inputFiles != null ? new HashMap<>(this.inputFiles) : new HashMap<>();
    }

    protected Map<String, String> finalEnv() throws IOException {
        return this.env != null ? new HashMap<>(this.env) : new HashMap<>();
    }

    protected List<String> finalCommandsWithInterpreter(String commandAsString) throws IOException {
        return BashService.finalCommandsWithInterpreter(
            this.interpreter,
            this.interpreterArgs,
            commandAsString,
            workingDirectory
        );
    }

    @SuppressWarnings("deprecation")
    protected ScriptOutput run(RunContext runContext, Supplier<String> supplier) throws Exception {
        Logger logger = runContext.logger();

        if (this.workingDirectory == null) {
            this.workingDirectory = runContext.tempDir();
        }

        additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());

        List<String> allOutputs = new ArrayList<>();

        // deprecated properties
        if (this.outputFiles != null && this.outputFiles.size() > 0) {
            allOutputs.addAll(this.outputFiles);
        }

        if (this.outputsFiles != null && this.outputsFiles.size() > 0) {
            allOutputs.addAll(this.outputsFiles);
        }

        if (files != null && files.size() > 0) {
            allOutputs.addAll(files);
        }

        Map<String, String> outputFiles = BashService.createOutputFiles(
            workingDirectory,
            allOutputs,
            additionalVars
        );

        BashService.createInputFiles(
            runContext,
            workingDirectory,
            this.finalInputFiles(runContext),
            additionalVars
        );

        String commandAsString = supplier.get();

        // run
        RunResult runResult = this.run(
            runContext,
            logger,
            workingDirectory,
            finalCommandsWithInterpreter(commandAsString),
            this.finalEnv(),
            this.defaultLogSupplier(logger, runContext)
        );

        // upload output files
        Map<String, URI> uploaded = new HashMap<>();

        outputFiles.
            forEach(throwBiConsumer((k, v) -> uploaded.put(k, runContext.putTempFile(new File(runContext.render(v, additionalVars))))));

        Map<String, Object> outputsVars = new HashMap<>();
        outputsVars.putAll(runResult.getStdOut().getOutputs());
        outputsVars.putAll(runResult.getStdErr().getOutputs());

        // output
        return ScriptOutput.builder()
            .exitCode(runResult.getExitCode())
            .stdOutLineCount(runResult.getStdOut().getLogsCount())
            .stdErrLineCount(runResult.getStdErr().getLogsCount())
            .warningOnStdErr(this.warningOnStdErr)
            .vars(outputsVars)
            .files(uploaded)
            .outputFiles(uploaded)
            .build();
    }

    protected LogSupplier defaultLogSupplier(Logger logger, RunContext runContext) {
        return (inputStream, isStdErr) -> {
            AbstractLogThread thread = new LogThread(logger, inputStream, isStdErr, runContext);
            thread.setName("bash-log-" + (isStdErr ? "-err" : "-out"));
            thread.start();

            return thread;
        };
    }

    protected RunResult run(RunContext runContext, Logger logger, Path workingDirectory, List<String> commandsWithInterpreter, Map<String, String> env,  LogSupplier logSupplier) throws Exception {
        ScriptRunnerInterface executor;
        if (this.runner == Runner.DOCKER) {
            executor = new DockerScriptRunner(runContext.getApplicationContext());
        } else {
            executor = new ProcessBuilderScriptRunner();
        }

        return executor.run(
            this,
            runContext,
            logger,
            workingDirectory,
            commandsWithInterpreter,
            env,
            logSupplier,
            additionalVars
        );
    }

    @NoArgsConstructor
    @Data
    public static class BashCommand <T> {
        private Map<String, Object> outputs;
        private List<AbstractMetricEntry<T>> metrics;
    }

    @FunctionalInterface
    public interface LogSupplier {
        AbstractLogThread call(InputStream inputStream, boolean isStdErr) throws Exception;
    }

    public static class LogThread extends AbstractLogThread {
        private final Logger logger;
        private final boolean isStdErr;
        private final RunContext runContext;

        public LogThread(Logger logger, InputStream inputStream, boolean isStdErr, RunContext runContext) {
            super(inputStream);

            this.logger = logger;
            this.isStdErr = isStdErr;
            this.runContext = runContext;
        }

        protected void call(String line) {
            outputs.putAll(BashService.parseOut(line, logger, runContext));

            if (isStdErr) {
                logger.warn(line);
            } else {
                logger.info(line);
            }
        }
    }

    @Getter
    @Builder
    public static class BashException extends Exception {
        private static final long serialVersionUID = 1L;

        private final int exitCode;
        private final int stdOutSize;
        private final int stdErrSize;

        public BashException(int exitCode, int stdOutSize, int stdErrSize) {
            super("Command failed with code " + exitCode);
            this.exitCode = exitCode;
            this.stdOutSize = stdOutSize;
            this.stdErrSize = stdErrSize;
        }
    }

    public enum Runner {
        PROCESS,
        DOCKER
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Introspected
    public static class DockerOptions {
        @Schema(
            title = "Docker api uri"
        )
        @PluginProperty(dynamic = true)
        @Builder.Default
        private final String dockerHost = "unix:///var/run/docker.sock";

        @Schema(
            title = "Docker config file",
            description = "Full file that can be used to configure private registries, ..."
        )
        @PluginProperty(dynamic = true)
        private String dockerConfig;

        @Schema(
            title = "Docker image to use"
        )
        @PluginProperty(dynamic = true)
        @NotNull
        @NotEmpty
        protected String image;

        @Schema(
            title = "Docker user to use"
        )
        @PluginProperty(dynamic = true)
        protected String user;

        @Schema(
            title = "Docker entrypoint to use"
        )
        @PluginProperty(dynamic = true)
        protected List<String> entryPoint;

        @Schema(
            title = "Docker extra host to use"
        )
        @PluginProperty(dynamic = true)
        protected List<String> extraHosts;

        @Schema(
            title = "Docker network mode to use"
        )
        @PluginProperty(dynamic = true)
        protected String networkMode;

        @Schema(
            title = "List of volumes to mount",
            description = "Must be a valid mount expression as string, example : `/home/user:/app`\n\n" +
                "Volumes mount are disabled by default for security reasons, you must enabled on server configuration with `kestra.tasks.scripts.docker.volume-enabled` to `true`"
        )
        @PluginProperty(dynamic = true)
        protected List<String> volumes;
    }
}
