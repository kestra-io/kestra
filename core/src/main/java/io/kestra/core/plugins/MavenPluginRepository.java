package io.kestra.core.plugins;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MavenPluginRepository {

    private static final Logger log = LoggerFactory.getLogger(MavenPluginRepository.class);
    private static final String DEFAULT_LOCAL_REPOSITORY_PREFIX = "kestra-plugins-m2-repository";
    private static final String DEFAULT_REPOSITORY_TYPE = "default";

    private final List<MavenPluginRepositoryConfig> repositoryConfigs;
    private final RepositorySystem system;
    private final RepositorySystemSession session;

    @Inject
    public MavenPluginRepository(List<MavenPluginRepositoryConfig> repositoryConfigs,
                                 @Nullable @Value("${kestra.plugins.local-repository-path}") String localRepositoryPath) {
        this.repositoryConfigs = repositoryConfigs;
        this.system = repositorySystem();
        this.session = repositorySystemSession(system, localRepositoryPath);
    }

    /**
     * Resolves the given dependencies.
     *
     * @param dependency The dependency to resolve.
     * @return the local {@link Path} of the resolved dependency.
     */
    public PluginArtifact resolve(String dependency) {
        return doResolve(buildRemoteRepositories(repositoryConfigs), dependency);
    }

    /**
     * Resolves the version of the given dependencies.
     *
     * @param dependency The dependency to resolve.
     * @return the local {@link Path} of the resolved dependency.
     */
    public List<String> listAllVersions(final String dependency) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(dependency);

            VersionRangeRequest request = new VersionRangeRequest();
            request.setArtifact(artifact.setVersion("[0,)")); // use a wide version range
            request.setRepositories(buildRemoteRepositories(this.repositoryConfigs));

            VersionRangeResult result = system.resolveVersionRange(session, request);
            return result.getVersions().stream().map(Object::toString).toList();
        } catch (VersionRangeResolutionException e) {
            log.debug("Failed to resolve all versions for '{}'", dependency);
            return List.of();
        }
    }


    /**
     * Resolves the given dependencies given the additional repositories.
     *
     * @param dependency   The dependency to resolve.
     * @param repositories The Maven repositories.
     * @return the local {@link Path} of the resolved dependency.
     */
    public PluginArtifact resolve(String dependency, List<MavenPluginRepositoryConfig> repositories) {
        List<RemoteRepository> allRepositories = new ArrayList<>();
        allRepositories.addAll(buildRemoteRepositories(this.repositoryConfigs));
        allRepositories.addAll(buildRemoteRepositories(repositories));

        return doResolve(allRepositories, dependency);
    }

    private PluginArtifact doResolve(List<RemoteRepository> repositories, String dependency) {
        ArtifactResult result = resolveArtifact(repositories, dependency);
        Path path = result.getArtifact().getFile().toPath();
        log.debug("Resolved Plugin '{}' with '{}'", dependency, path);
        return new PluginArtifact(
            result.getArtifact().getGroupId(),
            result.getArtifact().getArtifactId(),
            result.getArtifact().getExtension(),
            result.getArtifact().getClassifier(),
            result.getArtifact().getVersion(),
            path.toUri()
        );
    }

    private static List<RemoteRepository> buildRemoteRepositories(List<MavenPluginRepositoryConfig> repositoryConfigs) {
        return repositoryConfigs
            .stream()
            .map(repositoryConfig -> {
                var build = new RemoteRepository.Builder(
                    repositoryConfig.id(),
                    DEFAULT_REPOSITORY_TYPE,
                    repositoryConfig.url()
                );

                if (repositoryConfig.basicAuth() != null) {
                    var authenticationBuilder = new AuthenticationBuilder();
                    authenticationBuilder.addUsername(repositoryConfig.basicAuth().username());
                    authenticationBuilder.addPassword(repositoryConfig.basicAuth().password());
                    build.setAuthentication(authenticationBuilder.build());
                }

                return build.build();
            })
            .toList();
    }

    private static RepositorySystem repositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        return locator.getService(RepositorySystem.class);
    }

    private RepositorySystemSession repositorySystemSession(RepositorySystem system, String localRepositoryPath) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        if (localRepositoryPath == null) {
            try {
                final String tmpDir = Files.createTempDirectory(DEFAULT_LOCAL_REPOSITORY_PREFIX).toAbsolutePath().toString();

                localRepositoryPath = tmpDir;

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        FileUtils.deleteDirectory(new File(tmpDir));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        LocalRepository localRepo = new LocalRepository(localRepositoryPath);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private ArtifactResult resolveArtifact(List<RemoteRepository> repositories, String dependency) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(dependency);
            VersionRangeResult version = system.resolveVersionRange(session, new VersionRangeRequest(artifact, repositories, null));
            var artifactRequest = new ArtifactRequest(
                new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "jar", version.getHighestVersion().toString()),
                repositories,
                null
            );
            return system.resolveArtifact(session, artifactRequest);
        } catch (VersionRangeResolutionException | ArtifactResolutionException e) {
            throw new RuntimeException("Failed to resolve dependency: '" + dependency + "'", e);
        }
    }
}
