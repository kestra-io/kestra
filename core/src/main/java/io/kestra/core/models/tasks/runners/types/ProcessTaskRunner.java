package io.kestra.core.models.tasks.runners.types;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.runners.*;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Introspected
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
// all task runners are beta for now, but this one is stable as it was the one used before
@Plugin(beta = true, examples = {})
@Schema(
    title = "A task runner that runs task as a process on the Kestra host",
    description = "When the Kestra Worker that runs this process is terminated, the process will be terminated and the task fail."
)
public class ProcessTaskRunner extends TaskRunner {

    @Override
    public RunnerResult run(RunContext runContext, TaskCommands taskCommands, List<String> filesToUpload, List<String> filesToDownload) throws Exception {
        Logger logger = runContext.logger();
        AbstractLogConsumer defaultLogConsumer = taskCommands.getLogConsumer();

        ProcessBuilder processBuilder = new ProcessBuilder();

        Map<String, String> environment = processBuilder.environment();
        environment.putAll(this.env(runContext, taskCommands));

        processBuilder.directory(taskCommands.getWorkingDirectory().toFile());
        processBuilder.command(taskCommands.getCommands());

        Process process = processBuilder.start();
        long pid = process.pid();
        logger.debug("Starting command with pid {} [{}]", pid, String.join(" ", taskCommands.getCommands()));

        LogThread stdOut = new LogThread(process.getInputStream(), defaultLogConsumer, false);
        LogThread stdErr = new LogThread(process.getErrorStream(), defaultLogConsumer, true);

        stdOut.start();
        stdErr.start();

        try {
            int exitCode = process.waitFor();

            stdOut.join();
            stdErr.join();

            if (exitCode != 0) {
                throw new TaskException(exitCode, defaultLogConsumer.getStdOutCount(), defaultLogConsumer.getStdErrCount());
            } else {
                logger.debug("Command succeed with code {}", exitCode);
            }

            return new RunnerResult(exitCode, defaultLogConsumer);
        } catch (InterruptedException e) {
            logger.warn("Killing process {} for InterruptedException", pid);
            killDescendantsOf(process.toHandle(), logger);
            process.destroy();
            throw e;
        } finally {
            stdOut.join();
            stdErr.join();
        }
    }

    @Override
    protected Map<String, Object> runnerAdditionalVars(RunContext runContext, TaskCommands taskCommands) {
        return Map.of(
            ScriptService.VAR_WORKING_DIR, taskCommands.getWorkingDirectory().toString(),
            ScriptService.VAR_OUTPUT_DIR, taskCommands.getOutputDirectory().toString()
        );
    }

    private void killDescendantsOf(ProcessHandle process, Logger logger) {
        process.descendants().forEach(processHandle -> {
            if (!processHandle.destroy()) {
                logger.warn("Descendant process {} of {} couldn't be killed", processHandle.pid(), process.pid());
            }
        });
    }

    public static class LogThread extends Thread {
        private final InputStream inputStream;

        private final AbstractLogConsumer logConsumerInterface;

        private final boolean isStdErr;

        protected LogThread(InputStream inputStream, AbstractLogConsumer logConsumerInterface, boolean isStdErr) {
            this.inputStream = inputStream;
            this.logConsumerInterface = logConsumerInterface;
            this.isStdErr = isStdErr;
        }

        @Override
        public void run() {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        this.logConsumerInterface.accept(line, this.isStdErr);
                    }
                }
            } catch (Exception e) {
                try {
                    this.logConsumerInterface.accept(e.getMessage(), true);
                } catch (Exception ex) {
                    // do nothing if we cannot send the error message to the log consumer
                }
            }
        }
    }
}
