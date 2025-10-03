package io.kestra.core.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/**
 * Interface that can be implemented by classes supporting plugin versioning.
 *
 * @see Plugin
 */
public interface PluginVersioning {
    
    String TITLE = "Plugin Version";
    String DESCRIPTION = """
    Defines the version of the plugin to use.

    The version must follow the Semantic Versioning (SemVer) specification:
      - A single-digit MAJOR version (e.g., `1`).
      - A MAJOR.MINOR version (e.g., `1.1`).
      - A MAJOR.MINOR.PATCH version, optionally with any qualifier
        (e.g., `1.1.2`, `1.1.0-SNAPSHOT`).
    """;
    
    @Schema(
        title = TITLE,
        description = DESCRIPTION
    )
    String getVersion();
}
