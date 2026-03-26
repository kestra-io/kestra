package io.kestra.core.runners;

import java.nio.file.Path;
import java.util.Optional;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/**
 * Factory class for the constructing new {@link WorkingDir} objects.
 */
@Singleton
public class WorkingDirFactory {

    @Value("${kestra.tasks.tmp-dir.path}")
    protected Optional<String> tmpdirPath;

    /**
     * Creates a new {@link WorkingDir} instance.
     *
     * @return The {@link WorkingDir}.
     */
    public WorkingDir createWorkingDirectory() {
        return new LocalWorkingDir(getTmpDir());
    }

    private Path getTmpDir() {
        return Path.of(tmpdirPath.orElse(System.getProperty("java.io.tmpdir")));
    }
}
