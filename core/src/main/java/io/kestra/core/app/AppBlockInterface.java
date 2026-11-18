package io.kestra.core.app;

import io.kestra.core.models.annotations.Plugin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static io.kestra.core.utils.RegexPatterns.JAVA_IDENTIFIER_REGEX;

/**
 * Top-level marker interface for Kestra's plugin of type App.
 * <p>
 * Deserialized via {@link io.kestra.core.plugins.serdes.PluginDeserializer} — never add {@code @JsonTypeInfo} back,
 * since that would silently bypass the registered deserializer.
 */
@Plugin
public interface AppBlockInterface extends io.kestra.core.models.Plugin {
    @Schema(
        title = "The type of the block."
    )
    @NotNull
    @NotBlank
    @Pattern(regexp = JAVA_IDENTIFIER_REGEX)
    String getType();
}
