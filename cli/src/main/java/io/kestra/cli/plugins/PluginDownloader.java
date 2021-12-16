package io.kestra.cli.plugins;

import com.google.common.collect.ImmutableList;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class PluginDownloader {
    private List<RepositoryConfig> repositoryConfigs;
    private final RepositorySystem system;
    private final RepositorySystemSession session;

    @Inject
    public PluginDownloader(
        List<RepositoryConfig> repositoryConfigs,
        @Nullable @Value("${kestra.plugins.local-repository-path}") String localRepositoryPath
    ) {
        this.repositoryConfigs = repositoryConfigs;
        this.system = repositorySystem();
        this.session = repositorySystemSession(system, localRepositoryPath);
    }

    public void addRepository(RepositoryConfig repositoryConfig) {
        this.repositoryConfigs.add(repositoryConfig);
    }

    public List<URL> resolve(List<String> dependencies) throws DependencyResolutionException, MalformedURLException {
        List<RemoteRepository> repositories = remoteRepositories();

        ImmutableList.Builder<URL> urls = ImmutableList.builder();

        for (String dependency : dependencies) {
            log.debug("Resolving plugin {}", dependency);

            List<ArtifactResult> artifactResults = resolveArtifacts(repositories, dependency);
            List<URL> localUrls = resolveUrls(artifactResults);
            log.debug("Resolved Plugin {} with {}", dependency, localUrls);

            urls.addAll(localUrls);
        }

        return urls.build();
    }

    private List<RemoteRepository> remoteRepositories() {
        return repositoryConfigs
            .stream()
            .map(repositoryConfig ->
                new RemoteRepository.Builder(
                    repositoryConfig.getId(),
                    repositoryConfig.getType(),
                    repositoryConfig.getUrl()
                )
                .build()
            )
            .collect(Collectors.toList());
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
                localRepositoryPath = Files.createTempDirectory(this.getClass().getSimpleName().toLowerCase())
                    .toAbsolutePath()
                    .toString();
                new File(localRepositoryPath).deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        LocalRepository localRepo = new LocalRepository(localRepositoryPath);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private List<ArtifactResult> resolveArtifacts(List<RemoteRepository> repositories, String dependency) throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(dependency);

        DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter("jar");

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, "jar"));
        collectRequest.setRepositories(repositories);

        DependencyRequest depRequest = new DependencyRequest(collectRequest, classpathFlter);

        return system.resolveDependencies(session, depRequest).getArtifactResults();
    }

    private List<URL> resolveUrls(List<ArtifactResult> artifactResults) throws MalformedURLException {
        ImmutableList.Builder<URL> urls = ImmutableList.builder();
        for (ArtifactResult artifactResult : artifactResults) {
            URL url;
            url = artifactResult.getArtifact().getFile().toPath().toUri().toURL();
            urls.add(url);
        }
        return urls.build();
    }
}
