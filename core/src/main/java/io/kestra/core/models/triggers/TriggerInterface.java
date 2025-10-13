package io.kestra.core.models.triggers;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.PluginVersioning;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static io.kestra.core.utils.RegexPatterns.JAVA_IDENTIFIER_REGEX;

public interface TriggerInterface extends Plugin, PluginVersioning {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    @Schema(title = "A unique ID for the whole flow.")
    String getId();

    @NotNull
    @NotBlank
    @Pattern(regexp = JAVA_IDENTIFIER_REGEX)
    @Schema(title = "The class name for this current trigger.")
    String getType();

}
