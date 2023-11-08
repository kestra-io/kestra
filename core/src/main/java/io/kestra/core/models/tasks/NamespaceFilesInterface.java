package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface NamespaceFilesInterface {
    @Schema(
        title = "Inject namespace files",
        description = "Inject namespace file on this task. Inject all namespaces files when enabled or use filter to limit injected files."
    )
    @PluginProperty
    NamespaceFiles getNamespaceFiles();
}
