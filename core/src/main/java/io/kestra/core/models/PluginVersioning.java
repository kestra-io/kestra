package io.kestra.core.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Interface that can be implemented by classes supporting plugin versioning.
 *
 * @see Plugin
 */
public interface PluginVersioning {
    @Schema(title = "The version of the plugin to use.")
    String getVersion();
}
