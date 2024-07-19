package io.kestra.plugin.core.runner;

import io.github.pixee.security.BoundedLineReader;
import io.kestra.core.models.annotations.Example;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Introspected
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task runner that executes a task as a subprocess on the Kestra host.",
    description = """
        To access the task's working directory, use the `{{workingDir}}` Pebble expression or the `WORKING_DIR` environment variable. Input files and namespace files will be available in this directory.

        To generate output files you can either use the `outputFiles` task's property and create a file with the same name in the task's working directory, or create any file in the output directory which can be accessed by the `{{outputDir}}` Pebble expression or the `OUTPUT_DIR` environment variables.

        Note that:
        - This task runner is Operating System agnostic. You can use it also on Windows without any additional configuration.
        - When the Kestra Worker running this task is terminated, the process will be interrupted and re-created at worker restart."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Shell command.",
            code = """
                id: new-shell
                namespace: company.team

                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                    - echo "Hello World\"""",
            full = true
        ),
        @Example(
            title = "Pass input files to the task, execute a Shell command, then retrieve output files.",
            code = """
                id: new-shell-with-file
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    inputFiles:
                      data.txt: "{{inputs.file}}"
                    outputFiles:
                      - out.txt
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                    - cp {{workingDir}}/data.txt {{workingDir}}/out.txt""",
            full = true
        )
    }
)
public class Process extends TaskRunner {
    /** Convenient default instance to be used as task default value for a 'taskRunner' property. */
    public static final Process INSTANCE = Process.builder().type(Process.class.getName()).build();

    @Override
    public RunnerResult run(RunContext runContext, TaskCommands taskCommands, List<String> filesToDownload) throws Exception {
        Logger logger = runContext.logger();
        AbstractLogConsumer defaultLogConsumer = taskCommands.getLogConsumer();

        java.lang.ProcessBuilder processBuilder = new java.lang.ProcessBuilder();

        Map<String, String> environment = processBuilder.environment();
        environment.putAll(this.env(runContext, taskCommands));

        processBuilder.directory(taskCommands.getWorkingDirectory().toFile());
        processBuilder.command(taskCommands.getCommands());

        java.lang.Process process = processBuilder.start();
        long pid = process.pid();
        logger.debug("Starting command with pid {} [{}]", pid, String.join(" ", taskCommands.getCommands()));

        LogRunnable stdOutRunnable = new LogRunnable(process.getInputStream(), defaultLogConsumer, false);
        LogRunnable stdErrRunnable = new LogRunnable(process.getErrorStream(), defaultLogConsumer, true);
        Thread stdOut = Thread.startVirtualThread(stdOutRunnable);
        Thread stdErr = Thread.startVirtualThread(stdErrRunnable);


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
        Map<String, Object> vars = new HashMap<>();
        vars.put(ScriptService.VAR_WORKING_DIR, taskCommands.getWorkingDirectory());

        if (taskCommands.outputDirectoryEnabled()) {
            vars.put(ScriptService.VAR_OUTPUT_DIR, taskCommands.getOutputDirectory());
        }

        return vars;
    }

    private void killDescendantsOf(ProcessHandle process, Logger logger) {
        process.descendants().forEach(processHandle -> {
            if (!processHandle.destroy()) {
                logger.warn("Descendant process {} of {} couldn't be killed", processHandle.pid(), process.pid());
            }
        });
    }

    public static class LogRunnable implements Runnable {
        private final InputStream inputStream;

        private final AbstractLogConsumer logConsumerInterface;

        private final boolean isStdErr;

        protected LogRunnable(InputStream inputStream, AbstractLogConsumer logConsumerInterface, boolean isStdErr) {
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
                    while ((line = BoundedLineReader.readLine(bufferedReader, 5_000_000)) != null) {
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
