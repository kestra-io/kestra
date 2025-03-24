package io.kestra.core.plugins;

import java.net.URI;

/**
 * Metadata for a specific plugin artifact file.
 *
 * @param uri              The URI of the plugin artifact.
 * @param name             The name of the plugin artifact.
 * @param size             The size of the plugin artifact.
 * @param lastModifiedTime The last modified time of the plugin artifact.
 * @param creationTime     The creation time of the plugin artifact.
 */
public record PluginArtifactMetadata(
    URI uri,
    String name,
    long size,
    long lastModifiedTime,
    long creationTime) {

    /**
     * Gets a new {@link PluginArtifact} from this.
     *
     * @return a new {@link PluginArtifact}.
     */
    public PluginArtifact toPluginArtifact() {
        return PluginArtifact.fromFileName(this.name).relocateTo(uri);
    }
}
