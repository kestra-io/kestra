package org.kestra.core.tasks.scripts;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.annotations.OutputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

import static org.kestra.core.utils.Rethrow.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Execute a Bash script, command or set of commands."
)
@Example(
    title = "Single bash command",
    code = {
        "commands:",
        "- echo \"The current execution is : {{execution.id}}\""
    }
)
@Example(
    title = "Bash command that generate file in storage accessible through outputs",
    code = {
        "outputsFiles:",
        "- first",
        "- second",
        "commands:",
        "- echo \"1\" >> {{ outputFiles.first }}",
        "- echo \"2\" >> {{ outputFiles.second }}"
    }
)
@Example(
    title = "Bash with some inputs files",
    code = {
        "inputsFiles:",
        "  script.sh: |",
        "    echo {{ workingDir }}",
        "commands:",
        "- /bin/bash script.sh",
    }
)
public class Bash extends Task implements RunnableTask<Bash.Output> {
    @InputProperty(
        description = "The commands to run",
        body = {
            "Default command will be launched with `/bin/sh -c \"commands\"`"
        },
        dynamic = true
    )
    protected String[] commands;

    @Builder.Default
    @InputProperty(
        description = "Interpreter to used",
        body = {
            "Default is `/bin/sh`"
        },
        dynamic = false
    )
    protected String interpreter = "/bin/sh";

    @Builder.Default
    @InputProperty(
        description = "Interpreter args used",
        body = {
            "Default is `{\"-c\"}`"
        },
        dynamic = false
    )
    protected String[] interpreterArgs = {"-c"};

    @Builder.Default
    @InputProperty(
        description = "Exit if any non true return value",
        body = {
            "This tells bash that it should exit the script if any statement returns a non-true return value.",
            "The benefit of using -e is that it prevents errors snowballing into serious issues when they could " +
                "have been caught earlier."
        },
        dynamic = true
    )
    protected boolean exitOnFailed = true;

    @InputProperty(
        description = "The list of files that will be uploaded to internal storage, ",
        body = {
            "/!\\deprecated property, use `outputsFiles` property instead"
        },
        dynamic = true
    )
    protected List<String> files;

    @InputProperty(
        description = "Output file list that will be uploaded to internal storage",
        body = {
            "List of key that will generate temporary files.",
            "On the command, just can use with special variable named `outputFiles.key`.",
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ outputFiles.first }}`" +
                " and you used on others tasks using `{{ outputs.task-id.files.first }}`"
        },
        dynamic = true
    )
    protected List<String> outputsFiles;

    @InputProperty(
        description = "Input files are extra files supplied by user that make it simpler organize code.",
        body = {
            "Describe a files map that will be written and usable in execution context. In python execution context is in a temp folder, for bash scripts, you can reach files using a inputsDirectory variable like 'source {{inputsDirectory}}/myfile.sh' "
        },
        dynamic = true
    )
    protected Map<String, String> inputFiles;

    @Builder.Default
    protected transient List<File> cleanupDirectory = new ArrayList<>();
    protected transient Path workingDirectory;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        return run(runContext, throwFunction((additionalVars) -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
                if (this.workingDirectory != null) {
                    renderer.add("cd " + this.workingDirectory.toAbsolutePath().toString());
                }
            }

            // renderer command
            for (String command : this.commands) {
                renderer.add(runContext.render(command, additionalVars));
            }

            return String.join("\n", renderer);
        }));
    }

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

    protected Bash.Output run(RunContext runContext, Function<Map<String, Object>, String> function) throws Exception {
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

        this.cleanup();

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

        // output
        return Output.builder()
            .exitCode(exitCode)
            .stdOut(stdOut.getLogs())
            .stdErr(stdErr.getLogs())
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
        @OutputProperty(
            description = "The standard output of the commands"
        )
        private final List<String> stdOut;

        @OutputProperty(
            description = "The standard error of the commands"
        )
        private final List<String> stdErr;

        @OutputProperty(
            description = "The exit code of the whole execution"
        )
        private final int exitCode;


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
