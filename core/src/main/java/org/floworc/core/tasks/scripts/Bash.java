package org.floworc.core.tasks.scripts;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.tasks.Task;

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
public class Bash extends Task {
    private String[] commands;

    @Override
    public Void run() throws Exception {
        List<String> commands = Arrays.asList("/bin/sh", "-c", String.join("\n", this.commands));

        log.debug("Starting command [{}]", String.join("; ", this.commands));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commands);

        Process process = processBuilder.start();
        readInput(process.getInputStream(), false);
        readInput(process.getErrorStream(), true);

        // process.pid();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed with code " + exitCode);
        } else {
            log.debug("Command succeed with code " + exitCode);
        }

        return null;
    }

    private void readInput(InputStream inputStream, boolean isStdErr) {
        Thread thread = new Thread(() -> {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (isStdErr) {
                        log.warn(line);
                    } else {
                        log.info(line);
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
