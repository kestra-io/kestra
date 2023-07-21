package io.kestra.core.tasks.scripts.runners;

import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.AbstractLogThread;
import io.kestra.core.tasks.scripts.RunResult;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Deprecated
public class ProcessBuilderScriptRunner implements ScriptRunnerInterface {
    public RunResult run(
        AbstractBash abstractBash,
        RunContext runContext,
        Logger logger,
        Path workingDirectory,
        List<String> commandsWithInterpreter,
        Map<String, String> env,
        AbstractBash.LogSupplier logSupplier,
        Map<String, Object> additionalVars
    ) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();

        if (env != null && env.size() > 0) {
            Map<String, String> environment = processBuilder.environment();

            environment.putAll(env
                .entrySet()
                .stream()
                .map(throwFunction(r -> new AbstractMap.SimpleEntry<>(
                        runContext.render(r.getKey(), additionalVars),
                        runContext.render(r.getValue(), additionalVars)
                    )
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }

        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        processBuilder.command(commandsWithInterpreter);

        Process process = processBuilder.start();
        long pid = process.pid();
        logger.debug("Starting command with pid {} [{}]", pid, String.join(" ", commandsWithInterpreter));

        try {
            // logs
            AbstractLogThread stdOut = logSupplier.call(process.getInputStream(), false);
            AbstractLogThread stdErr = logSupplier.call(process.getErrorStream(), true);


            int exitCode = process.waitFor();

            stdOut.join();
            stdErr.join();

            if (exitCode != 0) {
                throw new AbstractBash.BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
            } else {
                logger.debug("Command succeed with code " + exitCode);
            }

            return new RunResult(exitCode, stdOut, stdErr);
        } catch (InterruptedException e) {
            logger.warn("Killing process {} for InterruptedException", pid);
            killDescendantsOf(process.toHandle(), logger);
            process.destroy();
            throw e;
        }
    }

    private void killDescendantsOf(ProcessHandle process, Logger logger) {
        process.descendants().forEach(processHandle -> {
            if (!processHandle.destroy()) {
                logger.warn("Descendant process {} of {} couldn't be killed", processHandle.pid(), process.pid());
            }
        });
    }
}
