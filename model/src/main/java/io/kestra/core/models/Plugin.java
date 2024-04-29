package io.kestra.core.models;

import io.kestra.core.models.annotations.Plugin.Id;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
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
        Objects.requireNonNull(plugin, "Cannot check if a plugin is internal from null");
        io.kestra.core.models.annotations.Plugin annotation = plugin.getAnnotation(io.kestra.core.models.annotations.Plugin.class);
        return Optional.ofNullable(annotation)
            .map(io.kestra.core.models.annotations.Plugin::internal)
            .orElse(false);
    }

    /**
     * Static helper method to get the id of a plugin.
     *
     * @param plugin The plugin type.
     * @return an optional string id.
     */
    static Optional<String> getId(final Class<?> plugin) {
        Objects.requireNonNull(plugin, "Cannot get plugin id from null");
        Id annotation = plugin.getAnnotation(Id.class);
        return Optional.ofNullable(annotation).map(Id::value).map(String::toLowerCase);
    }
}
