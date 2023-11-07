package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface NamespaceFilesInterface {
    @Schema(
        title = "Inject namespace files",
        description = "Inject namespace file on this task. If true, inject all namespaces files or use filter to limit injected files.",
        anyOf = {Boolean.class, NamespaceFiles.class}
    )
    @PluginProperty
    Object getNamespaceFiles();
}
