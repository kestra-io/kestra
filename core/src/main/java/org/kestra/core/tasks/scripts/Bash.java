package org.kestra.core.tasks.scripts;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Execute a Bash script, command or set of commands."
)
@Example(
    code = {
        "commands:",
        "- echo \"The current execution is : {{execution.id}}\""
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

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        // renderer templates
        List<String> renderer = new ArrayList<>();
        for (String command : this.commands) {
            renderer.add(runContext.render(command));
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

        // process.pid();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed with code " + exitCode);
        } else {
            logger.debug("Command succeed with code " + exitCode);
        }

        return Output.builder()
            .exitCode(exitCode)
            .stdOut(stdOut.getLogs())
            .stdErr(stdErr.getLogs())
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
        private List<String> stdOut;

        @OutputProperty(
            description = "The standard error of the commands"
        )
        private List<String> stdErr;

        @OutputProperty(
            description = "The exit code of the whole execution"
        )
        private int exitCode;
    }
}
