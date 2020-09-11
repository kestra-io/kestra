package org.kestra.core.tasks.scripts;

import com.google.common.collect.ImmutableMap;
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
import java.nio.file.Files;
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
        "files:",
        "- first",
        "- second",
        "commands:",
        "- echo \"1\" >> {{ temp.first }}",
        "- echo \"2\" >> {{ temp.second }}"
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
            "/!\\legacy property, use 'outputs' property instead"
        },
        dynamic = true
    )
    protected List<String> files;

    @InputProperty(
        description = "Output file list that will be uploaded to internal storage",
        body = {
            "List of key that will generate temporary files.",
            "On the command, just can use with special variable named `temp.key`.",
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ temp.first }}`" +
                " and you used on others tasks using `{{ outputs.task-id.files.first }}`"
        },
        dynamic = true
    )
    protected List<String> outputsFiles;

    @InputProperty(
        description = "Input files are extra files supplied by user that make it simpler organize code.",
        body = {
            "Describe a files map that will be written and usable in execution context. In python execution context is in a temp folder, for bash scripts, you can reach files using a scriptFolder variable like 'source {{scriptFolder}}/myfile.sh' "
        },
        dynamic = true
    )
    protected Map<String, String> inputFiles;

    protected volatile List<File> cleanupDirectory;
    protected volatile String tmpFolder;


    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        tmpFolder = Files.createTempDirectory("/tmp/").toString();
        return run(runContext, throwFunction((tempFiles) -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            // renderer command
            for (String command : this.commands) {
                renderer.add(runContext.render(
                    command,
                    tempFiles.size() > 0 ? ImmutableMap.of("temp", tempFiles, "scriptFolder", tmpFilesFolder()) : ImmutableMap.of("scriptFolder", tmpFilesFolder())
                ));
            }

            return String.join("\n", renderer);
        }));
    }

    protected void handleInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        if (inputFiles != null && inputFiles.size() > 0) {
            File tmpFileFolderHandler = new File(tmpFilesFolder());
            Files.createDirectories(Paths.get(tmpFilesFolder()));

            cleanupDirectory.add(tmpFileFolderHandler);
            cleanupDirectory.add(new File(tmpFolder));

            for (String fileName : inputFiles.keySet()) {
                String subFolder = tmpFilesFolder() + "/" + new File(fileName).getParent();
                if (!new File(subFolder).exists()) {
                    Files.createDirectories(Paths.get(subFolder));
                }
                String filePath = tmpFilesFolder() + "/" + fileName;
                BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
                writer.write(runContext.render(inputFiles.get(fileName)));
                writer.close();
            }
        }
    }

    protected Bash.Output run(RunContext runContext, Function<Map<String, String>, String> function) throws Exception {
        Logger logger = runContext.logger();

        // final command
        List<String> renderer = new ArrayList<>();
        cleanupDirectory = new ArrayList<File>();

        if (this.exitOnFailed) {
            renderer.add("set -o errexit");
        }

        List<String> outputs = new ArrayList<>();

        if (this.outputsFiles != null && this.outputsFiles.size() > 0) {
            outputs.addAll(this.outputsFiles);
        }

        if (files != null && files.size() > 0) {
            outputs.addAll(files);
        }

        this.handleInputFiles(runContext);

        // temporary files
        Map<String, String> tempFiles = new HashMap<>();
        outputs
            .forEach(throwConsumer(s -> {
                File tempFile = File.createTempFile(s + "_", ".tmp");

                tempFiles.put(s, tempFile.getAbsolutePath());
            }));

        String commandAsString = function.apply(tempFiles);

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
            throw new BashException(
                exitCode,
                stdOut.getLogs(),
                stdErr.getLogs()
            );
        } else {
            logger.debug("Command succeed with code " + exitCode);
        }

        // upload generate files
        Map<String, URI> uploaded = new HashMap<>();

        tempFiles.
            forEach(throwBiConsumer((k, v) -> {
                uploaded.put(k, runContext.putTempFile(new File(v)));
            }));

        // bash temp files
        if (bashTempFiles != null) {
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

    protected String tmpFilesFolder() {
        return tmpFolder + "-files";
    }

    protected LogThread readInput(Logger logger, InputStream inputStream, boolean isStdErr) {
        LogThread thread = new LogThread(logger, inputStream, isStdErr);
        thread.setName("bash-log");
        thread.start();

        return thread;
    }

    protected class LogThread extends Thread {
        private Logger logger;
        private InputStream inputStream;
        private boolean isStdErr;
        private List<String> logs = new ArrayList<>();

        protected LogThread(Logger logger, InputStream inputStream, boolean isStdErr) {
            this.logger = logger;
            this.inputStream = inputStream;
            this.isStdErr = isStdErr;
        }

        @Override
        public void run() {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    this.logs.add(line);
                    if (isStdErr) {
                        logger.warn(line);
                    } else {
                        logger.info(line);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public List<String> getLogs() {
            return logs;
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
