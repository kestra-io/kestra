package org.kestra.core.tasks.scripts;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
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
public class Bash extends Task implements RunnableTask {
    private String[] commands;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
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
