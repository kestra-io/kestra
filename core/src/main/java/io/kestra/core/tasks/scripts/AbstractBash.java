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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Deprecated
abstract public class AbstractBash extends Task {
    @Builder.Default
    @Schema(
        title = "Runner to use"
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected AbstractBash.Runner runner = Runner.PROCESS;

    @Schema(
        title = "Docker options when using runner `DOCKER`"
    )
    @PluginProperty
    protected DockerOptions dockerOptions;

    @Builder.Default
    @Schema(
        title = "Interpreter to used"
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected String interpreter = "/bin/sh";

    @Builder.Default
    @Schema(
        title = "Interpreter args used"
    )
    @PluginProperty
    protected String[] interpreterArgs = {"-c"};

    @Builder.Default
    @Schema(
        title = "Exit if any non true return value",
        description = "This tells bash that it should exit the script if any statement returns a non-true return value. \n" +
            "The benefit of using -e is that it prevents errors snowballing into serious issues when they could " +
            "have been caught earlier."
    )
    @PluginProperty
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
    @PluginProperty
    @Deprecated
    protected List<String> outputsFiles;

    @Schema(
        title = "Output file list that will be uploaded to internal storage",
        description = "List of key that will generate temporary files.\n" +
            "On the command, just can use with special variable named `outputFiles.key`.\n" +
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ outputFiles.first }}`" +
            " and you used on others tasks using `{{ outputs.taskId.outputFiles.first }}`"
    )
    @PluginProperty
    protected List<String> outputFiles;

    @Schema(
        title = "Output dirs list that will be uploaded to internal storage",
        description = "List of key that will generate temporary directories.\n" +
            "On the command, just can use with special variable named `outputDirs.key`.\n" +
            "If you add a files with `[\"myDir\"]`, you can use the special vars `echo 1 >> {[ outputDirs.myDir }}/file1.txt` " +
            "and `echo 2 >> {[ outputDirs.myDir }}/file2.txt` and both files will be uploaded to internal storage." +
            " Then you can used them on others tasks using `{{ outputs.taskId.outputFiles['myDir/file1.txt'] }}`"
    )
    @PluginProperty
    protected List<String> outputDirs;

    @Schema(
        title = "Input files are extra files that will be available in the script working directory.",
        description = "You can define the files as map or a JSON string." +
            "Each file can be defined inlined or can reference a file from Kestra's internal storage."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Object inputFiles;

    @Schema(
        title = "Additional environments variable to add for current process."
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
    @PluginProperty
    @NotNull
    protected Boolean warningOnStdErr = true;

    @Getter(AccessLevel.NONE)
    protected transient Path workingDirectory;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected transient Map<String, Object> additionalVars = new HashMap<>();

    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        return this.inputFiles != null ? new HashMap<>(BashService.transformInputFiles(runContext, this.inputFiles)) : new HashMap<>();
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

        List<String> allOutputDirs = new ArrayList<>();

        if (this.outputDirs != null && this.outputDirs.size() > 0) {
            allOutputDirs.addAll(this.outputDirs);
        }

        Map<String, String> outputDirs = BashService.createOutputFiles(
            workingDirectory,
            allOutputDirs,
            additionalVars,
            true
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

        // outputFiles
        outputFiles
            .forEach(throwBiConsumer((k, v) -> uploaded.put(k, runContext.putTempFile(new File(runContext.render(v, additionalVars))))));

        // outputDirs
        outputDirs
            .forEach(throwBiConsumer((k, v) -> {
                try (Stream<Path> walk = Files.walk(new File(runContext.render(v, additionalVars)).toPath())) {
                    walk
                        .filter(Files::isRegularFile)
                        .forEach(throwConsumer(path -> {
                            String filename = Path.of(
                                k,
                                Path.of(runContext.render(v, additionalVars)).relativize(path).toString()
                            ).toString();

                            uploaded.put(
                                filename,
                                runContext.putTempFile(path.toFile(), filename)
                            );
                        }));
                }
            }));

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
        private String dockerHost;

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

        @Schema(
            title = "Is a pull of image must be done before starting the container",
            description = "Mostly used for local image with registry"
        )
        @PluginProperty(dynamic = false)
        @Builder.Default
        protected Boolean pullImage = true;

        @Schema(
            title = "A list of request for devices to be sent to device drivers"
        )
        @PluginProperty(dynamic = false)
        protected List<DeviceRequest> deviceRequests;

        @Schema(
            title = "Limits cpu usage.",
            description = "By default, each container’s access to the host machine’s CPU cycles is unlimited. " +
                "You can set various constraints to limit a given container’s access to the host machine’s CPU cycles."
        )
        @PluginProperty(dynamic = false)
        protected Cpu cpu;

        @Schema(
            title = "Limits memory usage.",
            description = "Docker can enforce hard memory limits, which allow the container to use no more than a " +
                "given amount of user or system memory, or soft limits, which allow the container to use as much " +
                "memory as it needs unless certain conditions are met, such as when the kernel detects low memory " +
                "or contention on the host machine. Some of these options have different effects when used alone or " +
                "when more than one option is set."
        )
        @PluginProperty(dynamic = false)
        protected Memory memory;

        @SuperBuilder
        @NoArgsConstructor
        @Getter
        @Introspected
        @Schema(
            title = "A request for devices to be sent to device drivers"
        )
        public static class DeviceRequest {
            private String driver;
            private Integer count;
            private List<String> deviceIds;

            @Schema(
                title = "A list of capabilities; an OR list of AND lists of capabilities."
            )
            private List<List<String>> capabilities;

            @Schema(
                title = "Driver-specific options, specified as a key/value pairs.",
                description = "These options are passed directly to the driver."
            )
            private Map<String, String> options;
        }

        @SuperBuilder
        @NoArgsConstructor
        @Getter
        @Introspected
        public static class Cpu {
            @Schema(
                title = "Specify how much of the available CPU resources a container can use.",
                description = "For instance, if the host machine has two CPUs and you set `cpus:\"1.5\"`, the container is guaranteed at most one and a half of the CPUs"
            )
            private Long cpus;
        }

        @SuperBuilder
        @NoArgsConstructor
        @Getter
        @Introspected
        public static class Memory {
            @Schema(
                title = "The maximum amount of memory the container can use.",
                description = "That is, you must set the value to at least 6 megabytes."
            )
            @PluginProperty(dynamic = true)
            private String memory;

            @Schema(
                title = "The amount of memory this container is allowed to swap to disk",
                description = "If `memory` and `memorySwap` are set to the same value, this prevents containers from " +
                    "using any swap. This is because `memorySwap` is the amount of combined memory and swap that can be " +
                    "used, while `memory` is only the amount of physical memory that can be used."
            )
            @PluginProperty(dynamic = true)
            private String memorySwap;

            @Schema(
                title = "The amount of memory this container is allowed to swap to disk",
                description = "By default, the host kernel can swap out a percentage of anonymous pages used by a " +
                    "container. You can set `memorySwappiness` to a value between 0 and 100, to tune this percentage."
            )
            @PluginProperty(dynamic = true)
            private String memorySwappiness;

            @Schema(
                title = "Allows you to specify a soft limit smaller than --memory which is activated when Docker detects contention or low memory on the host machine.",
                description = "If you use `memoryReservation`, it must be set lower than `memory` for it to take precedence. " +
                    "Because it is a soft limit, it does not guarantee that the container doesn’t exceed the limit."
            )
            @PluginProperty(dynamic = true)
            private String memoryReservation;

            @Schema(
                title = "The maximum amount of kernel memory the container can use.",
                description = "The minimum allowed value is 4m. Because kernel memory cannot be swapped out, a " +
                    "container which is starved of kernel memory may block host machine resources, which can have " +
                    "side effects on the host machine and on other containers. " +
                    "See [--kernel-memory](https://docs.docker.com/config/containers/resource_constraints/#--kernel-memory-details) details."
            )
            @PluginProperty(dynamic = true)
            private String kernelMemory;

            @Schema(
                title = "By default, if an out-of-memory (OOM) error occurs, the kernel kills processes in a container.",
                description = "To change this behavior, use the `oomKillDisable` option. Only disable the OOM killer " +
                    "on containers where you have also set the `memory` option. If the `memory` flag is not set, the host " +
                    "can run out of memory and the kernel may need to kill the host system’s processes to free memory."
            )
            @PluginProperty(dynamic = false)
            private Boolean oomKillDisable;
        }
    }
}
