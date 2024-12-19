package io.kestra.core.plugins;

import java.util.List;

/**
 * Represents the result of a version resolution for an artifact.
 *
 * @param artifact The artifact that was resolved.
 * @param version  The resolved version.
 * @param versions The list of all available versions.
 * @param resolved {@code true} if version was resolved. Otherwise {@code false}.
 */
public record PluginResolutionResult(
    PluginArtifact artifact,
    String version,
    List<String> versions,
    boolean resolved
) {

}