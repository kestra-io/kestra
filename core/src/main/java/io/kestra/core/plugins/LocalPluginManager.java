package io.kestra.core.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static io.kestra.core.plugins.PluginManager.createLocalRepositoryIfNotExist;

/**
 * A {@link PluginManager} implementation managing plugin artifacts on local storage.
 */
@Singleton
public class LocalPluginManager implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger(LocalPluginManager.class);

    private final Provider<PluginRegistry> pluginRegistryProvider;

    private final MavenPluginDownloader mavenPluginDownloader;

    private final Path localRepositoryPath;

    /**
     * Creates a new {@link LocalPluginManager} instance.
     *
     * @param mavenPluginDownloader The {@link MavenPluginDownloader}.
     */
    public LocalPluginManager(final MavenPluginDownloader mavenPluginDownloader) {
        this(null, mavenPluginDownloader, null);
    }

    /**
     * Creates a new {@link LocalPluginManager} instance.
     *
     * @param pluginRegistryProvider The {@link PluginRegistry}.
     * @param mavenPluginDownloader       The {@link MavenPluginDownloader}.
     * @param localRepositoryPath    The local repository path used to stored plugins.
     */
    @Inject
    public LocalPluginManager(final Provider<PluginRegistry> pluginRegistryProvider,
                              final MavenPluginDownloader mavenPluginDownloader,
                              @Nullable @Value("${kestra.plugins.management.localRepositoryPath}") final String localRepositoryPath) {
        this.pluginRegistryProvider = pluginRegistryProvider;
        this.mavenPluginDownloader = mavenPluginDownloader;
        this.localRepositoryPath = PluginManager.getLocalManagedRepositoryPathOrDefault(localRepositoryPath);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void start() {
        // no-op
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<PluginArtifactMetadata> list() {
        try (Stream<Path> files = Files.list(localRepositoryPath)) {
            return files
                .filter(file -> Files.isRegularFile(file) && Files.isReadable(file) && file.toString().endsWith(".jar"))
                .map(file -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        return new PluginArtifactMetadata(
                            file.toUri(),
                            file.getFileName().toString(),
                            attrs.size(),
                            attrs.creationTime().toMillis(),
                            attrs.lastModifiedTime().toMillis()
                        );
                    } catch (IOException e) {
                        log.warn("Failed to get file attribute from file {}", file.getFileName());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public PluginArtifact install(PluginArtifact artifact,
                                  List<MavenPluginRepositoryConfig> repositoryConfigs,
                                  boolean installForRegistration,
                                  @Nullable Path localRepositoryPath) {
        Objects.requireNonNull(artifact, "cannot install null artifact");

        log.info("Installing managed plugin artifact '{}'", artifact);
        final PluginArtifact resolvedPluginArtifact = mavenPluginDownloader.resolve(artifact.toString(), repositoryConfigs);

        return install(resolvedPluginArtifact, installForRegistration, localRepositoryPath);
    }

    private PluginArtifact install(final PluginArtifact artifact,
                                   final boolean installForRegistration,
                                   Path localRepositoryPath) {

        localRepositoryPath = createLocalRepositoryIfNotExist(Optional.ofNullable(localRepositoryPath).orElse(this.localRepositoryPath));
        Path localPluginPath = getLocalPluginPath(localRepositoryPath, artifact);

        try {
            Files.createDirectories(localPluginPath.getParent());
            Files.copy(Path.of(artifact.uri()), localPluginPath, StandardCopyOption.REPLACE_EXISTING);

            if (installForRegistration && pluginRegistryProvider != null) {
                pluginRegistryProvider.get().register(localRepositoryPath);
            }
            log.info("Plugin '{}' installed successfully in local repository: {}", artifact, localRepositoryPath);
            return artifact.relocateTo(localPluginPath.toUri());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public PluginArtifact install(File file, boolean installForRegistration, @Nullable Path localRepositoryPath, boolean forceInstallOnExistingVersions) {
        try {
            PluginArtifact artifact = PluginArtifact.fromFile(file);
            log.info("Installing managed plugin artifact '{}'", artifact);
            return install(artifact, installForRegistration, localRepositoryPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<PluginArtifact> install(List<PluginArtifact> artifacts,
                                        List<MavenPluginRepositoryConfig> repositoryConfigs,
                                        boolean refreshPluginRegistry,
                                        @Nullable Path localRepositoryPath) {
        return artifacts.stream()
            .map(artifact -> install(artifact, repositoryConfigs, refreshPluginRegistry, localRepositoryPath))
            .toList();
    }

    private Path getLocalPluginPath(final Path localRepositoryPath, final PluginArtifact artifact) {
        return localRepositoryPath.resolve(artifact.toFileName());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<PluginArtifact> uninstall(List<PluginArtifact> artifacts, boolean refreshPluginRegistry, @Nullable Path localRepositoryPath) {

        final Path repositoryPath = Optional.ofNullable(localRepositoryPath).orElse(this.localRepositoryPath);

        final List<PluginArtifact> uninstalled = artifacts.stream()
            .map(artifact -> doUninstall(artifact, repositoryPath) ? artifact : null)
            .filter(Objects::nonNull)
            .toList();

        if (refreshPluginRegistry && pluginRegistryProvider != null) {
            pluginRegistryProvider.get().register(localRepositoryPath);
        }
        return uninstalled;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<PluginResolutionResult> resolveVersions(List<PluginArtifact> artifacts) {
        return mavenPluginDownloader.resolveVersions(artifacts);
    }

    private boolean doUninstall(final PluginArtifact artifact, final Path localRepositoryPath) {

        final Path localPluginPath = getLocalPluginPath(localRepositoryPath, artifact);

        if (Files.exists(localPluginPath)) {
            log.info("Removing plugin artifact from local repository: {}", localPluginPath);
            if (pluginRegistryProvider != null) {
                final PluginRegistry registry = pluginRegistryProvider.get();
                // Unregister all plugins from registry
                registry.unregister(new ArrayList<>(registry.plugins((plugin) -> {
                    if (plugin.getClassLoader() instanceof PluginClassLoader pluginClassLoader) {
                        URI location = URI.create(pluginClassLoader.location());
                        return localPluginPath.equals(Path.of(location));
                    }
                    return false;
                })));
            }

            try {
                if (Files.deleteIfExists(localPluginPath)) {
                    log.info("Removed plugin artifact from local repository: {}", localPluginPath);
                }
                return true;
            } catch (IOException e) {
                log.error(
                    "Unexpected error while removing plugin artifact from plugin repository: {}",
                    localPluginPath,
                    e
                );
                return false;
            }
        }
        return false;
    }
}
