package io.kestra.core.models;

import jakarta.validation.constraints.NotNull;

import java.util.Optional;

/**
 * Top-level interface for the Kestra plugins.
 */
public interface Plugin {

    /**
     * Gets the type of this plugin.
     *
     * @return  the string type of the plugin.
     */
    @NotNull
    default String getType() {
        return this.getClass().getCanonicalName();
    }

    /**
     * Static helper method to check whether a given plugin is internal.
     *
     * @param plugin    The plugin type.
     * @return  {@code true} if the plugin is internal.
     */
    static boolean isInternal(final Class<?> plugin) {
        io.kestra.core.models.annotations.Plugin annotation = plugin.getAnnotation(io.kestra.core.models.annotations.Plugin.class);
        return Optional.ofNullable(annotation)
            .map(io.kestra.core.models.annotations.Plugin::internal)
            .orElse(false);
    }
}
