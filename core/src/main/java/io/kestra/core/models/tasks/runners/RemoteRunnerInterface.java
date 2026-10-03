package io.kestra.core.models.tasks.runners;

import io.kestra.core.models.annotations.Beta;
import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;

public interface RemoteRunnerInterface {
    @Schema(
        title = "Whether to synchronize working directory from remote runner back to local one after run."
    )
    Property<Boolean> getSyncWorkingDirectory();

    /**
     * Whether this runner can read {@code kestra://} input files directly from internal storage (e.g. via
     * a signed URL) instead of having them staged through the worker. No {@code get}/{@code is} prefix, so
     * it is not exposed as a task property.
     */
    @Beta
    default boolean supportsDirectInputFiles() {
        return false;
    }
}
