package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface InputFilesInterface {
    @Schema(
        title = "The files to create on the local filesystem. It can be a map or a JSON object.",
        anyOf = {Map.class, String.class}
    )
    @PluginProperty(dynamic = true)
    Object getInputFiles();
}
