package io.kestra.cli.plugins;

import com.google.common.collect.ImmutableList;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class PluginDownloader {
    private final List<RepositoryConfig> repositoryConfigs;
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

    public List<URL> resolve(List<String> dependencies) throws MalformedURLException, ArtifactResolutionException, VersionRangeResolutionException {
        List<RemoteRepository> repositories = remoteRepositories();

        List<ArtifactResult> artifactResults = resolveArtifacts(repositories, dependencies);
        List<URL> localUrls = resolveUrls(artifactResults);
        log.debug("Resolved Plugin {} with {}", dependencies, localUrls);

        return localUrls;
    }

    private List<RemoteRepository> remoteRepositories() {
        return repositoryConfigs
            .stream()
            .map(repositoryConfig -> {
                var build = new RemoteRepository.Builder(
                    repositoryConfig.getId(),
                    repositoryConfig.getType(),
                    repositoryConfig.getUrl()
                );

                if (repositoryConfig.getBasicAuth() != null) {
                    var authenticationBuilder = new AuthenticationBuilder();
                    authenticationBuilder.addUsername(repositoryConfig.getBasicAuth().getUsername());
                    authenticationBuilder.addPassword(repositoryConfig.getBasicAuth().getPassword());

                    build.setAuthentication(authenticationBuilder.build());
                }

                return build.build();
            })
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
                final String tempDirectory = Files.createTempDirectory(this.getClass().getSimpleName().toLowerCase())
                    .toAbsolutePath()
                    .toString();

                localRepositoryPath = tempDirectory;

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        FileUtils.deleteDirectory(new File(tempDirectory));
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

    private List<ArtifactResult> resolveArtifacts(List<RemoteRepository> repositories, List<String> dependencies) throws ArtifactResolutionException, VersionRangeResolutionException {
        List<ArtifactResult> results = new ArrayList<>(dependencies.size());
        for (String dependency: dependencies) {
            var artifact = new DefaultArtifact(dependency);
            var version = system.resolveVersionRange(session, new VersionRangeRequest(artifact, repositories, null));
            var artifactRequest = new ArtifactRequest(
                new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "jar", version.getHighestVersion().toString()),
                repositories,
                null
            );
            var artifactResult = system.resolveArtifact(session, artifactRequest);
            results.add(artifactResult);
        }
        return results;
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
