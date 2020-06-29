package org.kestra.core.tasks.scripts;

import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
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
import java.util.*;

import static org.kestra.core.utils.Rethrow.throwBiConsumer;
import static org.kestra.core.utils.Rethrow.throwConsumer;

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
    title = "Bash command that generate file in storage accessible through ouputs",
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
            "All command will be launched with `/bin/sh -c \"commands\"`"
        },
        dynamic = true
    )
    private String[] commands;

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
    private boolean exitOnFailed = true;

    @InputProperty(
        description = "The list of files that will be uploaded to internal storage",
        body = {
            "List of key that will generate temporary files.",
            "On the command, just can use with special variable named `temp.key`.",
            "If you add a files with `[\"first\"]`, you can use the special vars `echo 1 >> {[ temp.first }}`" +
                " and you used on others tasks using `{{ outputs.task-id.files.first }}`"
        },
        dynamic = true
    )
    private List<String> files;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        // final command
        List<String> renderer = new ArrayList<>();

        if (this.exitOnFailed) {
            renderer.add("set -o errexit");
        }

        // temporary files
        Map<String, String> tempFiles = new HashMap<>();
        if (files != null && files.size() > 0) {
            files
                .forEach(throwConsumer(s -> {
                    File tempFile = File.createTempFile(s + "_", ".tmp");

                    tempFiles.put(s, tempFile.getAbsolutePath());
                }));
        }

        // renderer command
        for (String command : this.commands) {
            renderer.add(runContext.render(
                command,
                tempFiles.size() > 0 ? ImmutableMap.of("temp", tempFiles) : ImmutableMap.of()
            ));
        }

        logger.debug("Starting command [{}]", String.join("; ", renderer));

        // start
        List<String> commands = Arrays.asList("/bin/sh", "-c", String.join("\n", renderer));
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commands);
        Process process = processBuilder.start();

        // logs
        LogThread stdOut = readInput(logger, process.getInputStream(), false);
        LogThread stdErr = readInput(logger, process.getErrorStream(), true);

        int exitCode = process.waitFor();

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

        // output
        return Output.builder()
            .exitCode(exitCode)
            .stdOut(stdOut.getLogs())
            .stdErr(stdErr.getLogs())
            .files(uploaded)
            .build();
    }

    private LogThread readInput(Logger logger, InputStream inputStream, boolean isStdErr) {
        LogThread thread = new LogThread(logger, inputStream, isStdErr);
        thread.setName("bash-log");
        thread.start();

        return thread;
    }

    private static class LogThread extends Thread {
        private Logger logger;
        private InputStream inputStream;
        private boolean isStdErr;
        private List<String> logs = new ArrayList<>();

        private LogThread(Logger logger, InputStream inputStream, boolean isStdErr) {
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
