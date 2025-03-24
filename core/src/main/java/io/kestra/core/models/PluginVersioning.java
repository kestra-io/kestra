package io.kestra.core.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Interface that can be implemented by classes supporting plugin versioning.
 *
 * @see Plugin
 */
public interface PluginVersioning {

    @Pattern(regexp="\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?|([a-zA-Z0-9]+)")
    @Schema(title = "The version of the plugin to use.")
    String getVersion();
}
