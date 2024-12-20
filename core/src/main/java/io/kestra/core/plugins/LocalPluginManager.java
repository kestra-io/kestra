package io.kestra.core.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.kestra.core.plugins.PluginManager.createLocalRepositoryIfNotExist;

/**
 * A {@link PluginManager} implementation managing plugin artifacts on local storage.
 */
@Singleton
public class LocalPluginManager implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger(LocalPluginManager.class);

    private final Provider<PluginRegistry> pluginRegistryProvider;

    private final MavenPluginRepository pluginRepository;

    private final Path defaultLocalRepositoryPath;

    /**
     * Creates a new {@link LocalPluginManager} instance.
     *
     * @param pluginRepository           The {@link MavenPluginRepository}.
     */
    @Inject
    public LocalPluginManager(final MavenPluginRepository pluginRepository) {
        this(null, pluginRepository, null);
    }

    /**
     * Creates a new {@link LocalPluginManager} instance.
     *
     * @param pluginRegistryProvider     The {@link PluginRegistry}.
     * @param pluginRepository           The {@link MavenPluginRepository}.
     * @param defaultLocalRepositoryPath The local repository path used to stored plugins.
     */
    @Inject
    public LocalPluginManager(final Provider<PluginRegistry> pluginRegistryProvider,
                              final MavenPluginRepository pluginRepository,
                              @Nullable @Value("${kestra.plugins.management.localRepositoryPath}") final String defaultLocalRepositoryPath) {
        this.pluginRegistryProvider = pluginRegistryProvider;
        this.pluginRepository = pluginRepository;
        this.defaultLocalRepositoryPath = PluginManager.getLocalManagedRepositoryPathOrDefault(defaultLocalRepositoryPath);
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
    public PluginArtifact install(PluginArtifact artifact,
                                  List<MavenPluginRepositoryConfig> repositoryConfigs,
                                  boolean installForRegistration,
                                  @Nullable Path localRepositoryPath) {
        Objects.requireNonNull(artifact, "cannot install null artifact");

        final PluginArtifact resolvedPluginArtifact = pluginRepository.resolve(artifact.toString(), repositoryConfigs);

        localRepositoryPath = createLocalRepositoryIfNotExist(Optional.ofNullable(localRepositoryPath).orElse(this.defaultLocalRepositoryPath));
        Path localPluginPath = getLocalPluginPath(localRepositoryPath, resolvedPluginArtifact);

        try {
            Files.createDirectories(localPluginPath.getParent());
            Files.copy(Path.of(resolvedPluginArtifact.uri()), localPluginPath, StandardCopyOption.REPLACE_EXISTING);

            if (installForRegistration && pluginRegistryProvider != null) {
                pluginRegistryProvider.get().register(localRepositoryPath);
            }
            log.info("Plugin '{}' installed successfully in local repository: {}", artifact, localRepositoryPath);
            return resolvedPluginArtifact.relocateTo(localPluginPath.toUri());
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

        final Path repositoryPath = Optional.ofNullable(localRepositoryPath).orElse(this.defaultLocalRepositoryPath);

        final List<PluginArtifact> uninstalled = artifacts.stream()
            .map(artifact -> doUninstall(artifact, repositoryPath) ? artifact : null)
            .filter(Objects::nonNull)
            .toList();

        if (refreshPluginRegistry && pluginRegistryProvider != null) {
            pluginRegistryProvider.get().register(localRepositoryPath);
        }
        return uninstalled;
    }

    private boolean doUninstall(final PluginArtifact artifact, final Path localRepositoryPath) {

        final Path localPluginPath = getLocalPluginPath(localRepositoryPath, artifact);

        if (Files.exists(localPluginPath)) {
            if (pluginRegistryProvider != null) {
                final PluginRegistry registry = pluginRegistryProvider.get();
                // Unregister all plugins from registry
                registry.unregister(registry.plugins((plugin) -> {
                    if (plugin.getClassLoader() instanceof PluginClassLoader pluginClassLoader) {
                        return localPluginPath.toString().equals(pluginClassLoader.location());
                    }
                    return false;
                }).stream().toList());
            }

            try {
                Files.deleteIfExists(localPluginPath);
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
