package io.kestra.core.app;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Top-level marker interface for Kestra's plugin of type App.
 */
@Plugin
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.PROPERTY)
public interface AppPluginInterface extends io.kestra.core.models.Plugin {
    @Schema(
        title = "The type of the app."
    )
    @NotNull
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    String getType();
}
