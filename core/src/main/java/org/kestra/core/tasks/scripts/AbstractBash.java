package org.kestra.core.tasks.scripts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.annotations.PluginProperty;
import org.kestra.core.models.executions.AbstractMetricEntry;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static org.kestra.core.utils.Rethrow.throwBiConsumer;
import static org.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class AbstractBash extends Task implements RunnableTask<AbstractBash.Output> {
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
        description ="/!\\deprecated property, use `outputsFiles` property instead",
        deprecated = true
    )
    @PluginProperty(dynamic = true)
    @Deprecated
    protected List<String> files;

    @Schema(
        title = "Output file list that will be uploaded to internal storage",
        description = "List of key that will generate temporary files.\n" +
            "On the command, just can use with special variable named `outputFiles.key`.\n" +
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ outputFiles.first }}`" +
            " and you used on others tasks using `{{ outputs.task-id.files.first }}`"
    )
    @PluginProperty(dynamic = false)
    protected List<String> outputsFiles;

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

    @Builder.Default
    protected transient List<File> cleanupDirectory = new ArrayList<>();
    protected transient Path workingDirectory;
    protected static transient ObjectMapper mapper = JacksonMapper.ofJson();

    protected Map<String, String> handleOutputFiles(Map<String, Object> additionalVars) throws IOException {
        List<String> outputs = new ArrayList<>();

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

    protected void handleInputFiles(Map<String, Object> additionalVars, RunContext runContext) throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        if (inputFiles != null && inputFiles.size() > 0) {
            for (String fileName : inputFiles.keySet()) {
                File file = new File(fileName);

                // path with "/", create the subfolders
                if (file.getParent() != null) {
                    Path subFolder = Paths.get(
                        tmpWorkingDirectory(additionalVars).toAbsolutePath().toString(),
                        new File(fileName).getParent()
                    );

                    if (!subFolder.toFile().exists()) {
                        Files.createDirectories(subFolder);
                    }
                }

                String filePath = tmpWorkingDirectory(additionalVars) + "/" + fileName;
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

    protected AbstractBash.Output run(RunContext runContext, Function<Map<String, Object>, String> function) throws Exception {
        Logger logger = runContext.logger();
        Map<String, Object> additionalVars = new HashMap<>();

        Map<String, String> outputFiles = this.handleOutputFiles(additionalVars);
        this.handleInputFiles(additionalVars, runContext);

        String commandAsString = function.apply(additionalVars);

        File bashTempFiles = null;
        // https://www.in-ulm.de/~mascheck/various/argmax/ MAX_ARG_STRLEN (131072)
        if (commandAsString.length() > 131072) {
            bashTempFiles = File.createTempFile("bash", ".sh");
            Files.write(bashTempFiles.toPath(), commandAsString.getBytes());

            commandAsString = this.interpreter + " " + bashTempFiles.getAbsolutePath();
        }

        logger.debug("Starting command [{}]", commandAsString);

        // build the final commands
        List<String> commands = new ArrayList<>(Collections.singletonList(this.interpreter));
        commands.addAll(Arrays.asList(this.interpreterArgs));
        commands.add(commandAsString);

        // start
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commands);
        Process process = processBuilder.start();

        // logs
        LogThread stdOut = readInput(logger, process.getInputStream(), false);
        LogThread stdErr = readInput(logger, process.getErrorStream(), true);

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            stdOut.join();
            stdErr.join();

            throw new BashException(
                exitCode,
                stdOut.getLogs(),
                stdErr.getLogs()
            );
        } else {
            logger.debug("Command succeed with code " + exitCode);
        }

        // upload output files
        Map<String, URI> uploaded = new HashMap<>();

        outputFiles.
            forEach(throwBiConsumer((k, v) -> {
                uploaded.put(k, runContext.putTempFile(new File(v)));
            }));

        // bash temp files
        if (bashTempFiles != null) {
            //noinspection ResultOfMethodCallIgnored
            bashTempFiles.delete();
        }

        this.cleanup();

        Map<String, Object> outputs = new HashMap<>();
        outputs.putAll(parseOut(runContext, stdOut.getLogs()));
        outputs.putAll(parseOut(runContext, stdErr.getLogs()));

        // output
        return Output.builder()
            .exitCode(exitCode)
            .stdOutLineCount(stdOut.getLogs().size())
            .stdErrLineCount(stdErr.getLogs().size())
            .vars(outputs)
            .files(uploaded)
            .build();
    }

    protected void cleanup() throws IOException {
        for (File folder : cleanupDirectory) {
            FileUtils.deleteDirectory(folder);
        }
    }

    protected Path tmpWorkingDirectory(Map<String, Object> additionalVars) throws IOException {
        if (this.workingDirectory == null) {
            this.workingDirectory = Files.createTempDirectory("working-dir");
            this.cleanupDirectory.add(workingDirectory.toFile());
            additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());
        }

        return this.workingDirectory;
    }

    protected Map<String, Object> parseOut(RunContext runContext, List<String> outs) throws JsonProcessingException {
        Map<String, Object> outputs = new HashMap<>();

        for (String out : outs) {
            // captures output per line
            String pattern = "^::(\\{.*\\})::$";

            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(out);

            if (m.find()) {
                BashCommand<?> bashCommand = mapper.readValue(m.group(1), BashCommand.class);

                if (bashCommand.outputs != null) {
                    outputs.putAll(bashCommand.outputs);
                }


                if (bashCommand.metrics != null) {
                    bashCommand.metrics.forEach(runContext::metric);
                }
            }
        }

        return outputs;
    }

    @NoArgsConstructor
    @Data
    public static class BashCommand <T> {
        private Map<String, Object> outputs;
        private List<AbstractMetricEntry<T>> metrics;

    }

    protected LogThread readInput(Logger logger, InputStream inputStream, boolean isStdErr) {
        LogThread thread = new LogThread(logger, inputStream, isStdErr);
        thread.setName("bash-log");
        thread.start();

        return thread;
    }

    protected static class LogThread extends Thread {
        private final Logger logger;
        private final InputStream inputStream;
        private final boolean isStdErr;
        private final List<String> logs = new ArrayList<>();

        protected LogThread(Logger logger, InputStream inputStream, boolean isStdErr) {
            this.logger = logger;
            this.inputStream = inputStream;
            this.isStdErr = isStdErr;
        }

        @Override
        public void run() {
            synchronized (this) {
                try {
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            this.logs.add(line);
                            if (isStdErr) {
                                logger.warn(line);
                            } else {
                                logger.info(line);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public List<String> getLogs() {
            synchronized (this) {
                return logs;
            }
        }
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        @Schema(
            title = "The value extract from output of the commands"
        )
        private final Map<String, Object> vars;

        @Schema(
            title = "The standard output line count"
        )
        private final int stdOutLineCount;

        @Schema(
            title = "The standard error line count"
        )
        private final int stdErrLineCount;

        @Schema(
            title = "The exit code of the whole execution"
        )
        @NotNull
        private final int exitCode;

        @Schema(
            title = "The output files uri in Kestra internal storage"
        )
        @PluginProperty(additionalProperties = URI.class)
        private final Map<String, URI> files;
    }

    @Getter
    @Builder
    public static class BashException extends Exception {
        public BashException(int exitCode, List<String> stdOut, List<String> stdErr) {
            super("Command failed with code " + exitCode + " and stdErr '" + String.join("\n", stdErr) + "'");
            this.exitCode = exitCode;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        private final int exitCode;
        private final List<String> stdOut;
        private final List<String> stdErr;
    }
}
