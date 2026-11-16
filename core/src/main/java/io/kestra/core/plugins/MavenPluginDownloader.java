package io.kestra.core.plugins;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.configuration.PluginsConfiguration;
import io.kestra.core.utils.Version;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for resolving plugins from a Maven repository.
 */
@Singleton
@Slf4j
public class MavenPluginDownloader implements Closeable {
    private static final String DEFAULT_LOCAL_REPOSITORY_PREFIX = "kestra-plugins-m2-repository";
    private static final String DEFAULT_REPOSITORY_TYPE = "default";
    private static final String HTTP_PROXY_HOST = System.getProperty("http.proxyHost");
    private static final String HTTP_PROXY_PORT = System.getProperty("http.proxyPort");
    private static final String HTTPS_PROXY_HOST = System.getProperty("https.proxyHost");
    private static final String HTTPS_PROXY_PORT = System.getProperty("https.proxyPort");
    // Maven waits 30 minutes for the next byte by default, and an interrupt cannot end that wait, so a stalled download would hold its thread and the artifact lock.
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(2);
    private static final String LOCK_WAIT_SECONDS = "aether.syncContext.named.time";
    private static final long DEFAULT_LOCK_WAIT_SECONDS = 30;

    public static final String LATEST = "latest";

    private final List<MavenPluginRepositoryConfig> repositoryConfigs;
    private final RepositorySystem system;
    private final RepositorySystemSession session;

    @Inject
    public MavenPluginDownloader(List<MavenPluginRepositoryConfig> repositoryConfigs,
        @Nullable PluginsConfiguration pluginsConfiguration) {
        this.repositoryConfigs = repositoryConfigs;
        this.system = new RepositorySystemSupplier().get();
        this.session = repositorySystemSession(system, pluginsConfiguration != null ? pluginsConfiguration : new PluginsConfiguration(null, null));
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
            result.getExceptions().stream()
                .filter(e -> !isNotFound(e))
                .findFirst()
                .ifPresent(e -> log.warn("Failed to list the versions of '{}' from at least one Maven repository, so the list may be incomplete.", dependency, e));
            return result.getVersions().stream().map(Object::toString).toList();
        } catch (VersionRangeResolutionException e) {
            log.debug("Failed to resolve all versions for '{}'", dependency);
            return List.of();
        }
    }

    /**
     * Resolves the given dependencies given the additional repositories.
     *
     * @param dependency The dependency to resolve.
     * @param repositories The Maven repositories.
     * @return the local {@link Path} of the resolved dependency.
     */
    public PluginArtifact resolve(String dependency, List<MavenPluginRepositoryConfig> repositories) {
        return resolve(dependency, repositories, null);
    }

    /**
     * Resolves the given dependencies given the additional repositories, reporting byte-level
     * transfer progress to the provided listener.
     *
     * @param dependency The dependency to resolve.
     * @param repositories The Maven repositories.
     * @param listener optional transfer listener; {@code null} disables progress reporting.
     * @return the resolved {@link PluginArtifact}.
     */
    public PluginArtifact resolve(
        @NonNull String dependency,
        @NonNull List<MavenPluginRepositoryConfig> repositories,
        @Nullable TransferListener listener) {
        List<RemoteRepository> allRepositories = new ArrayList<>();
        allRepositories.addAll(buildRemoteRepositories(this.repositoryConfigs));
        allRepositories.addAll(buildRemoteRepositories(repositories));

        return doResolve(allRepositories, dependency, effectiveSession(listener));
    }

    private PluginArtifact doResolve(List<RemoteRepository> repositories, String dependency) {
        return doResolve(repositories, dependency, this.session);
    }

    private PluginArtifact doResolve(List<RemoteRepository> repositories, String dependency, RepositorySystemSession session) {
        PluginArtifact result = resolveArtifact(repositories, dependency, new DefaultArtifact(dependency).isSnapshot() ? alwaysUpdated(session) : session);
        log.debug("Resolved Plugin '{}' with '{}'", dependency, result.uri());
        return result;
    }

    /** A snapshot is republished in place: check the remote on every resolution, not once a day and per (server-long) session. */
    private static RepositorySystemSession alwaysUpdated(RepositorySystemSession session) {
        DefaultRepositorySystemSession copy = new DefaultRepositorySystemSession(session);
        copy.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        copy.setConfigProperty("aether.updateCheckManager.sessionState", "bypass");
        return copy;
    }

    /** Returns the base session or a shallow copy with the given listener attached. */
    private RepositorySystemSession effectiveSession(@Nullable TransferListener listener) {
        if (listener == null) {
            return this.session;
        }
        DefaultRepositorySystemSession copy = new DefaultRepositorySystemSession(this.session);
        copy.setTransferListener(listener);
        return copy;
    }

    public List<PluginResolutionResult> resolveVersions(final List<PluginArtifact> artifacts) {
        return artifacts.stream()
            .map(artifact ->
            {
                List<String> versions = listAllVersions(artifact.toCoordinates());

                final List<Version> parsedVersions = versions.stream().map(Version::of).sorted().toList();

                if (versions.isEmpty()) {
                    return new PluginResolutionResult(artifact, null, List.of(), false);
                }

                final List<String> sortedVersions = parsedVersions.stream().map(Version::toString).toList();
                if (artifact.version().equalsIgnoreCase(LATEST)) {
                    return new PluginResolutionResult(artifact, Version.getLatest(parsedVersions).toString(), sortedVersions, true);
                }

                return versions.contains(artifact.version()) ? new PluginResolutionResult(artifact, artifact.version(), versions, true)
                    : new PluginResolutionResult(artifact, null, sortedVersions, false);
            })
            .toList();
    }

    private static List<RemoteRepository> buildRemoteRepositories(List<MavenPluginRepositoryConfig> repositoryConfigs) {
        return repositoryConfigs
            .stream()
            .map(repositoryConfig ->
            {
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

                // Honor JDK proxy settings
                if (repositoryConfig.url().startsWith("http") && HTTP_PROXY_HOST != null) {
                    Proxy proxy = new Proxy(Proxy.TYPE_HTTP, HTTP_PROXY_HOST, HTTP_PROXY_PORT != null ? Integer.parseInt(HTTP_PROXY_PORT) : 80);
                    build.setProxy(proxy);
                }
                if (repositoryConfig.url().startsWith("https") && HTTPS_PROXY_HOST != null) {
                    Proxy proxy = new Proxy(Proxy.TYPE_HTTPS, HTTPS_PROXY_HOST, HTTPS_PROXY_PORT != null ? Integer.parseInt(HTTPS_PROXY_PORT) : 443);
                    build.setProxy(proxy);
                }

                return build.build();
            })
            .toList();
    }

    private RepositorySystemSession repositorySystemSession(RepositorySystem system, PluginsConfiguration configuration) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        Duration readTimeout = Optional.ofNullable(configuration.readTimeout()).orElse(DEFAULT_READ_TIMEOUT);
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, Math.toIntExact(readTimeout.toMillis()));
        // A retry of a stalled download waits for its read timeout to release the artifact lock instead of failing on it.
        session.setConfigProperty(LOCK_WAIT_SECONDS, readTimeout.toSeconds() + DEFAULT_LOCK_WAIT_SECONDS);

        String localRepositoryPath = configuration.localRepositoryPath();

        if (localRepositoryPath == null) {
            try {
                final String tmpDir = Files.createTempDirectory(DEFAULT_LOCAL_REPOSITORY_PREFIX).toAbsolutePath().toString();

                localRepositoryPath = tmpDir;

                Runtime.getRuntime().addShutdownHook(new Thread(() ->
                {
                    try {
                        FileUtils.deleteDirectory(new File(tmpDir));
                    } catch (IOException e) {
                        throw new KestraRuntimeException(e);
                    }
                }));
            } catch (IOException e) {
                throw new KestraRuntimeException(e);
            }
        }

        LocalRepository localRepo = new LocalRepository(localRepositoryPath);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private PluginArtifact resolveArtifact(List<RemoteRepository> repositories, String dependency, RepositorySystemSession session) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(dependency);
            VersionRangeResult version = system.resolveVersionRange(session, new VersionRangeRequest(artifact, repositories, null));
            if (version.getHighestVersion() == null) {
                // A repository holding the artifact but no version in the range records no exception at all.
                if (version.getExceptions().stream().allMatch(MavenPluginDownloader::isNotFound)) {
                    throw withCauses(new InvalidException(dependency, "No version matching plugin artifact `%s` was found in the configured Maven repositories (%s).".formatted(dependency, repositoryIds(repositories))), version.getExceptions());
                }
                throw withCauses(resolutionFailed(dependency), version.getExceptions());
            }

            final String highestVersion = version.getHighestVersion().toString();
            ArtifactRequest artifactRequest = new ArtifactRequest(
                new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "jar", highestVersion),
                repositories,
                null
            );
            ArtifactResult result = system.resolveArtifact(session, artifactRequest);
            return new PluginArtifact(
                result.getArtifact().getGroupId(),
                result.getArtifact().getArtifactId(),
                result.getArtifact().getExtension(),
                result.getArtifact().getClassifier(),
                // Use the version from ArtifactRequest and not the one from the ArtifactResult.
                // Otherwise, SNAPSHOT version will result in a timestamped version string.
                highestVersion.endsWith("-SNAPSHOT") ? highestVersion : result.getArtifact().getVersion(),
                result.getArtifact().getPath().toUri()
            );
        } catch (ArtifactResolutionException e) {
            List<Exception> exceptions = e.getResults().stream().flatMap(result -> result.getExceptions().stream()).toList();
            if (isAbsentFromEveryRepository(exceptions)) {
                throw new InvalidException(dependency, "Plugin artifact `%s` was not found in any of the configured Maven repositories (%s).".formatted(dependency, repositoryIds(repositories)), e);
            }
            throw withCauses(resolutionFailed(dependency), List.of(e));
        } catch (VersionRangeResolutionException e) {
            // The Maven version range resolver only throws when the version constraint cannot be parsed.
            throw new InvalidException(dependency, "Plugin artifact `%s` has an invalid version or version range.".formatted(dependency), e);
        }
    }

    /**
     * True only when every repository answered that the artifact does not exist: a repository that could not be
     * reached or refused the credentials leaves the artifact's existence unknown, which is a server-side failure.
     */
    private static boolean isAbsentFromEveryRepository(final List<Exception> exceptions) {
        List<Exception> causes = exceptions.stream()
            .flatMap(e -> e instanceof VersionResolutionException versionException ? versionException.getResult().getExceptions().stream() : Stream.of(e))
            .toList();
        return !causes.isEmpty() && causes.stream().allMatch(MavenPluginDownloader::isNotFound);
    }

    private static boolean isNotFound(final Exception exception) {
        return exception instanceof ArtifactNotFoundException || exception instanceof MetadataNotFoundException;
    }

    // Names repositories by id only, since a configured URL may embed credentials.
    private static String repositoryIds(final List<RemoteRepository> repositories) {
        return repositories.isEmpty()
            ? "none"
            : repositories.stream().map(RemoteRepository::getId).distinct().collect(Collectors.joining(", "));
    }

    private static KestraRuntimeException resolutionFailed(final String dependency) {
        return new KestraRuntimeException("Failed to resolve plugin artifact `%s`. Check that the configured Maven repositories are reachable and that their credentials are valid.".formatted(dependency));
    }

    private static <T extends RuntimeException> T withCauses(final T exception, final List<Exception> causes) {
        if (!causes.isEmpty()) {
            exception.initCause(causes.getFirst());
            causes.stream().skip(1).forEach(exception::addSuppressed);
        }
        return exception;
    }

    /**
     * {@inheritDoc}
     */
    @PreDestroy
    @Override
    public void close() throws IOException {
        try {
            system.shutdown();
        } catch (MultiRuntimeException e) {
            log.warn("Error while shutting down Maven repository", e);
        }
    }
}
