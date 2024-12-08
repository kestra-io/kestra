package io.kestra.core.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing Kestra's plugins.
 */
public interface PluginManager {

    /**
     * Installs the given plugin artifact.
     *
     * @param artifact               the plugin artifact.
     * @param repositoryConfigs      the addition repository configs.
     * @param installForRegistration specify whether plugin artifacts should be scanned and registered.
     * @param localRepositoryPath    the optional local repository path to install artifact.
     * @return The URI of the installed plugin.
     */
    PluginArtifact install(PluginArtifact artifact,
                           List<MavenPluginRepositoryConfig> repositoryConfigs,
                           boolean installForRegistration,
                           @Nullable Path localRepositoryPath);


    /**
     * Installs the given plugin artifact.
     *
     * @param artifacts             the list of plugin artifacts.
     * @param repositoryConfigs     the addition repository configs.
     * @param refreshPluginRegistry specify whether the plugin registry should be refreshed.
     * @param localRepositoryPath   the optional local repository path to install artifact.
     * @return The URIs of the installed plugins.
     */
    List<PluginArtifact> install(List<PluginArtifact> artifacts,
                                 List<MavenPluginRepositoryConfig> repositoryConfigs,
                                 boolean refreshPluginRegistry,
                                 @Nullable Path localRepositoryPath);

    /**
     * Uninstall the given plugin artifact.
     *
     * @param artifacts             the plugin artifacts to be uninstalled.
     * @param refreshPluginRegistry specify whether the plugin registry should be refreshed.
     * @param localRepositoryPath   the optional local repository path to install artifact.
     */
    List<PluginArtifact> uninstall(List<PluginArtifact> artifacts, boolean refreshPluginRegistry, @Nullable Path localRepositoryPath);

    /**
     * Static helper method to resolve the given local repository path.
     *
     * @param path the local repository path.
     * @return the repository path or the default one.
     */
    static Path getLocalManagedRepositoryPathOrDefault(final @Nullable String path) {
        Path resolved = Optional.ofNullable(path)
            .map(Path::of)
            .orElseGet(() -> Path
                .of(System.getProperty("java.io.tmpdir"))
                .resolve("kestra/plugins-repository")
            );
        return createLocalRepositoryIfNotExist(resolved);
    }

    static Path createLocalRepositoryIfNotExist(final Path resolved) {
        if (!Files.exists(resolved)) {
            try {
                Files.createDirectories(resolved);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create local repository for plugins", e);
            }
        }
        return resolved;
    }

}
