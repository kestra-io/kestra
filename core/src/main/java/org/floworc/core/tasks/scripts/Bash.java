package org.floworc.core.tasks.scripts;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class Bash extends Task implements RunnableTask {
    private String[] commands;

    @Override
    public Void run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        List<String> commands = Arrays.asList("/bin/sh", "-c", String.join("\n", this.commands));

        logger.debug("Starting command [{}]", String.join("; ", this.commands));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commands);

        Process process = processBuilder.start();
        readInput(logger, process.getInputStream(), false);
        readInput(logger, process.getErrorStream(), true);

        // process.pid();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed with code " + exitCode);
        } else {
            logger.debug("Command succeed with code " + exitCode);
        }

        return null;
    }

    private void readInput(Logger logger, InputStream inputStream, boolean isStdErr) {
        Thread thread = new Thread(() -> {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (isStdErr) {
                        logger.warn(line);
                    } else {
                        logger.info(line);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        thread.setName("bash-log");
        thread.start();
    }
}
