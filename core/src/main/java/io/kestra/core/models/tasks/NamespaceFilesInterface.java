package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface NamespaceFilesInterface {
    @Schema(
        title = "Inject namespace files.",
        description = "Inject namespace files to this task. When enabled, it will, by default, load all namespace files into the working directory. However, you can use the `include` or `exclude` properties to limit which namespace files will be injected."
    )
    @PluginProperty
    NamespaceFiles getNamespaceFiles();
}
