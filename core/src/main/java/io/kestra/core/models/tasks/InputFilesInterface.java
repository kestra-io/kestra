package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public interface InputFilesInterface {
    @Schema(
        title = "The files to create on the working. It can be a map or a JSON object.",
        description = """
            Each file can be defined:
            - Inline with its content
            - As a URI, supported schemes are `kestra` for internal storage files, `file` for host local files, and `nsfile` for namespace files.
            """,
        oneOf = {Map.class, String.class}
    )
    @PluginProperty(dynamic = true)
    Object getInputFiles();
}
