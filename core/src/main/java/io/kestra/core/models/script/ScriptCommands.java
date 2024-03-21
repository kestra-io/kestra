package io.kestra.core.models.script;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Interface for the commands passed to a Script runner.
 */
public interface ScriptCommands {
    String getContainerImage();

    AbstractLogConsumer getLogConsumer();

    List<String> getCommands();

    Map<String, Object> getAdditionalVars();

    Path getWorkingDirectory();

    Path getOutputDirectory();

    Map<String, String> getEnv();
}
