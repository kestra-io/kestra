package io.kestra.core.tasks.scripts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.utils.ExecutorsUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.*;

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
            "context is in a temp folder, for bash scripts, you can reach files using a inputsDirectory variable " +
            "like 'source {{inputsDirectory}}/myfile.sh' "
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
    @Getter(AccessLevel.NONE)
    protected transient List<File> cleanupDirectory = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    protected transient Path workingDirectory;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected transient Map<String, Object> additionalVars = new HashMap<>();

    protected Map<String, String> handleOutputFiles() {
        List<String> outputs = new ArrayList<>();

        if (this.outputFiles != null && this.outputFiles.size() > 0) {
            outputs.addAll(this.outputFiles);
        }

        if (this.outputsFiles != null && this.outputsFiles.size() > 0) {
            outputs.addAll(this.outputsFiles);
        }

        if (files != null && files.size() > 0) {
            outputs.addAll(files);
        }

        Map<String, String> outputFiles = new HashMap<>();
        if (outputs.size() > 0) {
            outputs
                .forEach(throwConsumer(s -> {
                    File tempFile = File.createTempFile(s + "_", ".tmp");

                    outputFiles.put(s, tempFile.getAbsolutePath());
                }));

            additionalVars.put("temp", outputFiles);
            additionalVars.put("outputFiles", outputFiles);
        }

        return outputFiles;
    }

    protected void handleInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        if (inputFiles != null && inputFiles.size() > 0) {
            Path workingDirectory = tmpWorkingDirectory();

            for (String fileName : inputFiles.keySet()) {
                File file = new File(fileName);

                // path with "/", create the subfolders
                if (file.getParent() != null) {
                    Path subFolder = Paths.get(
                        workingDirectory.toAbsolutePath().toString(),
                        new File(fileName).getParent()
                    );

                    if (!subFolder.toFile().exists()) {
                        Files.createDirectories(subFolder);
                    }
                }

                String filePath = workingDirectory + "/" + fileName;
                String render = runContext.render(inputFiles.get(fileName), additionalVars);

                if (render.startsWith("kestra://")) {
                    try (
                        InputStream inputStream = runContext.uriToInputStream(new URI(render));
                        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(filePath))
                    ) {
                        int byteRead;
                        while ((byteRead = inputStream.read()) != -1) {
                            outputStream.write(byteRead);
                        }
                        outputStream.flush();
                    }
                } else {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                        writer.write(render);
                    }
                }
            }
        }
    }

    protected List<String> finalCommandsWithInterpreter(String commandAsString) throws IOException {
        // build the final commands
        List<String> commandsWithInterpreter = new ArrayList<>(Collections.singletonList(this.interpreter));

        File bashTempFiles = null;
        // https://www.in-ulm.de/~mascheck/various/argmax/ MAX_ARG_STRLEN (131072)
        if (commandAsString.length() > 131072) {
            bashTempFiles = File.createTempFile("bash", ".sh", this.tmpWorkingDirectory().toFile());
            Files.write(bashTempFiles.toPath(), commandAsString.getBytes());

            commandAsString = bashTempFiles.getAbsolutePath();
        } else {
            commandsWithInterpreter.addAll(Arrays.asList(this.interpreterArgs));
        }

        commandsWithInterpreter.add(commandAsString);

        return commandsWithInterpreter;
    }

    protected ScriptOutput run(RunContext runContext, Supplier<String> supplier) throws Exception {
        Logger logger = runContext.logger();

        Map<String, String> outputFiles = this.handleOutputFiles();
        this.handleInputFiles(runContext);

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
            forEach(throwBiConsumer((k, v) -> {
                uploaded.put(k, runContext.putTempFile(new File(v)));
            }));

        this.cleanup();

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
        logger.debug("Starting command [{}]", String.join(" ", commandsWithInterpreter));

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

        // logs
        AbstractLogThread stdOut = logSupplier.call(process.getInputStream(), false);
        AbstractLogThread stdErr = logSupplier.call(process.getErrorStream(), true);

        int exitCode = process.waitFor();

        stdOut.join();
        stdErr.join();
        process.destroy();

        if (exitCode != 0) {
            throw new BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
        } else {
            logger.debug("Command succeed with code " + exitCode);
        }

        return new RunResult(exitCode, stdOut, stdErr);
    }

    protected void cleanup() throws IOException {
        for (File folder : cleanupDirectory) {
            FileUtils.deleteDirectory(folder);
        }
    }

    protected Path tmpWorkingDirectory() throws IOException {
        if (this.workingDirectory == null) {
            this.workingDirectory = Files.createTempDirectory("working-dir");
            this.cleanupDirectory.add(workingDirectory.toFile());
            additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());
        }

        return this.workingDirectory;
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
        protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();
        private static final Pattern PATTERN = Pattern.compile("^::(\\{.*\\})::$");

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
            this.parseOut(line, logger, runContext);

            if (isStdErr) {
                logger.warn(line);
            } else {
                logger.info(line);
            }
        }

        protected void parseOut(String line, Logger logger, RunContext runContext)  {
            Matcher m = PATTERN.matcher(line);

            if (m.find()) {
                try {
                    BashCommand<?> bashCommand = MAPPER.readValue(m.group(1), BashCommand.class);

                    if (bashCommand.outputs != null) {
                        outputs.putAll(bashCommand.outputs);
                    }

                    if (bashCommand.metrics != null) {
                        bashCommand.metrics.forEach(runContext::metric);
                    }
                }
                catch (JsonProcessingException e) {
                    logger.warn("Invalid outputs '{}'", e.getMessage(), e);
                }
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
