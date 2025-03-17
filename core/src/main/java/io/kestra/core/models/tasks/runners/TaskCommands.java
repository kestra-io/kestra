package io.kestra.core.models.tasks.runners;

import io.kestra.core.models.property.Property;
import lombok.With;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * Interface for the commands passed to a TaskRunner.
 */
public interface TaskCommands {
    String getContainerImage();

    AbstractLogConsumer getLogConsumer();

    Property<List<String>> getInterpreter();

    Property<List<String>> getBeforeCommands();

    Property<List<String>> getCommands();

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

    default List<Path> relativeWorkingDirectoryFilesPaths() throws IOException {
        return this.relativeWorkingDirectoryFilesPaths(false);
    }

    default List<Path> relativeWorkingDirectoryFilesPaths(boolean includeDirectories) throws IOException {
        Path workingDirectory = this.getWorkingDirectory();
        if (workingDirectory == null) {
            return Collections.emptyList();
        }

        try (Stream<Path> walk = Files.walk(workingDirectory)) {
            Stream<Path> filtered = includeDirectories ? walk : walk.filter(path -> !Files.isDirectory(path));
            Path outputDirectory = this.getOutputDirectory();
            if (outputDirectory != null) {
                filtered = filtered.filter(Predicate.not(path -> path.startsWith(outputDirectory)));
            }

            return filtered
                .map(workingDirectory::relativize)
                .toList();
        }
    }
}
