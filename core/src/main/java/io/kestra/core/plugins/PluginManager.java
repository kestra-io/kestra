package io.kestra.core.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing Kestra's plugins.
 */
public interface PluginManager extends AutoCloseable {

    /**
     * Starts this manager.
     */
    void start();

    /**
     * Checks whether this manager is ready.
     * <p>
     * This method should return {@code true} when this manager is fully started.
     * @see #start()
     *
     * @return {@code true} if the manager is ready.
     */
    boolean isReady();

    /**
     * Gets the list of plugin artifact managed this by class.
     *
     * @return The list of {@link PluginArtifact}.
     */
    List<PluginArtifactMetadata> list();

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
     * @param file                           the plugin JAR file.
     * @param installForRegistration         specify whether plugin artifacts should be scanned and registered.
     * @param localRepositoryPath            the optional local repository path to install artifact.
     * @param forceInstallOnExistingVersions specify whether plugin should be forced install upon the existing one
     * @return The URI of the installed plugin.
     */
    PluginArtifact install(final File file,
                           boolean installForRegistration,
                           @Nullable Path localRepositoryPath,
                           boolean forceInstallOnExistingVersions);

    /**
     * Installs the given plugin artifact.
     *
     * @param artifacts              the list of plugin artifacts.
     * @param repositoryConfigs      the addition repository configs.
     * @param installForRegistration specify whether the plugin registry should be refreshed.
     * @param localRepositoryPath    the optional local repository path to install artifact.
     * @return The URIs of the installed plugins.
     */
    List<PluginArtifact> install(List<PluginArtifact> artifacts,
                                 List<MavenPluginRepositoryConfig> repositoryConfigs,
                                 boolean installForRegistration,
                                 @Nullable Path localRepositoryPath);

    /**
     * Uninstall the given plugin artifact.
     *
     * @param artifacts             the plugin artifacts to be uninstalled.
     * @param refreshPluginRegistry specify whether the plugin registry should be refreshed.
     * @param localRepositoryPath   the optional local repository path to install artifact.
     */
    List<PluginArtifact> uninstall(List<PluginArtifact> artifacts,
                                   boolean refreshPluginRegistry,
                                   @Nullable Path localRepositoryPath);

    /**
     * Resolves the version for the given artifacts.
     *
     * @param artifacts The list of artifacts to resolve.
     * @return The list of results.
     */
    List<PluginResolutionResult> resolveVersions(List<PluginArtifact> artifacts);

    @Override
    default  void close() throws Exception {

    }

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
