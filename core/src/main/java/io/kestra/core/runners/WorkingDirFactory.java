package io.kestra.core.runners;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import io.kestra.core.runners.configuration.TasksConfiguration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory class for the constructing new {@link WorkingDir} objects.
 */
@Singleton
public class WorkingDirFactory {

    private static final String WORKING_DIRECTORY_PREFIX = "working-directory-";

    @Inject
    protected TasksConfiguration tasksConfiguration;

    /**
     * Creates a new {@link WorkingDir} instance.
     *
     * @return The {@link WorkingDir}.
     */
    public WorkingDir createWorkingDirectory() {
        return new LocalWorkingDir(getTmpDir());
    }

    /**
     * Creates the {@link WorkingDir} shared by all the tasks of a WorkingDirectory on this worker.
     *
     * @param executionId The id of the execution of the WorkingDirectory.
     * @param workingDirectoryId The id of the task run of the WorkingDirectory.
     * @return The {@link WorkingDir}, always the same folder for the same ids.
     */
    public WorkingDir createWorkingDirectory(String executionId, String workingDirectoryId) {
        return new LocalWorkingDir(getTmpDir(), WORKING_DIRECTORY_PREFIX + executionId + "-" + workingDirectoryId);
    }

    /**
     * Deletes what the WorkingDirectories of the given execution left on this worker, for an execution that will not
     * run their close job, such as a killed one.
     */
    public void deleteWorkingDirectories(String executionId) throws IOException {
        Path tmpDir = getTmpDir();
        if (!Files.isDirectory(tmpDir)) {
            return;
        }
        String prefix = WORKING_DIRECTORY_PREFIX + executionId + "-";
        try (Stream<Path> entries = Files.list(tmpDir)) {
            for (Path entry : entries.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
                FileUtils.forceDelete(entry.toFile());
            }
        }
    }

    private Path getTmpDir() {
        return Optional.ofNullable(tasksConfiguration.tmpDir())
            .map(TasksConfiguration.TmpDir::path)
            .map(Path::of)
            .orElse(Path.of(System.getProperty("java.io.tmpdir")));
    }
}
