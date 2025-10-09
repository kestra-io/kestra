package io.kestra.core.models.tasks.runners;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

public interface RemoteRunnerInterface {
    @Schema(
        title = "Whether to synchronize working directory from remote runner back to local one after run."
    )
    Property<Boolean> getSyncWorkingDirectory();
}
