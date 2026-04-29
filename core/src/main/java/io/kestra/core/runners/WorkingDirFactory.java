package io.kestra.core.runners;

import java.nio.file.Path;

import io.kestra.core.runners.configuration.WorkingDirConfiguration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory class for the constructing new {@link WorkingDir} objects.
 */
@Singleton
public class WorkingDirFactory {

    @Inject
    protected WorkingDirConfiguration workingDirConfiguration;

    /**
     * Creates a new {@link WorkingDir} instance.
     *
     * @return The {@link WorkingDir}.
     */
    public WorkingDir createWorkingDirectory() {
        return new LocalWorkingDir(getTmpDir());
    }

    private Path getTmpDir() {
        String path = workingDirConfiguration.path();
        return Path.of(path != null ? path : System.getProperty("java.io.tmpdir"));
    }
}
