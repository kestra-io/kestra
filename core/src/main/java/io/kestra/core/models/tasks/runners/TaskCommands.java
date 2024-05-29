package io.kestra.core.models.tasks.runners;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;


/**
 * Interface for the commands passed to a TaskRunner.
 */
public interface TaskCommands {
    String getContainerImage();

    AbstractLogConsumer getLogConsumer();

    List<String> getCommands();

    Map<String, Object> getAdditionalVars();

    Path getWorkingDirectory();

    Path getOutputDirectory();

    Map<String, String> getEnv();

    Boolean getEnableOutputDirectory();

    default boolean outputDirectoryEnabled() {
        return Boolean.TRUE.equals(this.getEnableOutputDirectory());
    }

    Duration getTimeout();

    TargetOS getTargetOS();
}
