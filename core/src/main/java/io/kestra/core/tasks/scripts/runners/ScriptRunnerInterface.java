package io.kestra.core.tasks.scripts.runners;

import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.RunResult;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ScriptRunnerInterface {
    RunResult run(
        AbstractBash abstractBash,
        RunContext runContext,
        Logger logger,
        Path workingDirectory,
        List<String> commandsWithInterpreter,
        Map<String, String> env,
        AbstractBash.LogSupplier logSupplier,
        Map<String, Object> additionalVars
    ) throws Exception;
}
