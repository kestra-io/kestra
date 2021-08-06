package io.kestra.core.tasks.scripts;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class AbstractBash extends Task {
    @Builder.Default
    @Schema(
        description = "Interpreter to used"
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
        description ="use `outputsFiles` property instead",
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

    @Getter(AccessLevel.NONE)
    protected transient Path workingDirectory;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected transient Map<String, Object> additionalVars = new HashMap<>();

    protected Map<String, String> finalInputFiles() throws IOException {
        return this.inputFiles;
    }

    protected List<String> finalCommandsWithInterpreter(String commandAsString) throws IOException {
        return BashService.finalCommandsWithInterpreter(
            this.interpreter,
            this.interpreterArgs,
            commandAsString,
            workingDirectory
        );
    }

    protected ScriptOutput run(RunContext runContext, Supplier<String> supplier) throws Exception {
        Logger logger = runContext.logger();

        if (this.workingDirectory == null) {
            this.workingDirectory = runContext.tempDir();
        }

        additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());

        Map<String, String> outputFiles = BashService.createOutputFiles(
            workingDirectory,
            this.outputFiles,
            additionalVars
        );

        BashService.createInputFiles(
            runContext,
            workingDirectory,
            this.finalInputFiles(),
            additionalVars
        );

        String commandAsString = supplier.get();

        // run
        RunResult runResult = this.run(
            runContext,
            logger,
            workingDirectory,
            finalCommandsWithInterpreter(commandAsString),
            this.env,
            (inputStream, isStdErr) -> {
                AbstractLogThread thread = new LogThread(logger, inputStream, isStdErr, runContext);
                thread.setName("bash-log-" + (isStdErr ? "-err" : "-out"));
                thread.start();

                return thread;
            }
        );

        // upload output files
        Map<String, URI> uploaded = new HashMap<>();

        outputFiles.
            forEach(throwBiConsumer((k, v) -> uploaded.put(k, runContext.putTempFile(new File(runContext.render(v, additionalVars))))));

        Map<String, Object> outputs = new HashMap<>();
        outputs.putAll(runResult.getStdOut().getOutputs());
        outputs.putAll(runResult.getStdErr().getOutputs());

        // output
        return ScriptOutput.builder()
            .exitCode(runResult.getExitCode())
            .stdOutLineCount(runResult.getStdOut().getLogsCount())
            .stdErrLineCount(runResult.getStdErr().getLogsCount())
            .vars(outputs)
            .files(uploaded)
            .outputFiles(uploaded)
            .build();
    }

    protected RunResult run(RunContext runContext, Logger logger, Path workingDirectory, List<String> commandsWithInterpreter, Map<String, String> env,  LogSupplier logSupplier) throws Exception {
        // start
        ProcessBuilder processBuilder = new ProcessBuilder();

        if (env != null && env.size() > 0) {
            Map<String, String> environment = processBuilder.environment();

            environment.putAll(env
                .entrySet()
                .stream()
                .map(throwFunction(r -> new AbstractMap.SimpleEntry<>(
                        runContext.render(r.getKey()),
                        runContext.render(r.getValue())
                    )
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }

        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        processBuilder.command(commandsWithInterpreter);

        Process process = processBuilder.start();
        long pid = process.pid();
        logger.debug("Starting command with pid {} [{}]", pid, String.join(" ", commandsWithInterpreter));

        try {
            // logs
            AbstractLogThread stdOut = logSupplier.call(process.getInputStream(), false);
            AbstractLogThread stdErr = logSupplier.call(process.getErrorStream(), true);


            int exitCode = process.waitFor();

            stdOut.join();
            stdErr.join();

            if (exitCode != 0) {
                throw new BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
            } else {
                logger.debug("Command succeed with code " + exitCode);
            }

            return new RunResult(exitCode, stdOut, stdErr);
        } catch (InterruptedException e) {
            logger.warn("Killing process {} for InterruptedException", pid);
            process.destroy();
            throw e;
        }
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
}
