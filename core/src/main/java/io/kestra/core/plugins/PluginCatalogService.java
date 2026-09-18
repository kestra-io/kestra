package io.kestra.core.plugins;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.Version;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Services for retrieving available plugin artifacts for Kestra.
 */
@Slf4j
public class PluginCatalogService {

    private static final Duration MAX_CACHE_DURATION = Duration.ofHours(1);

    /**
     * Minimum delay before retrying the hosted catalog after a failed or empty load. Package-private so tests can shorten it.
     */
    static Duration retryDelay = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    private CompletableFuture<List<PluginManifest>> plugins;

    private List<PluginManifest> loaded = List.of();

    private Instant cacheLastLoaded = Instant.now();
    private Instant retryNotBefore = Instant.MIN;
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);

    private final boolean icons;
    private final boolean oss;

    private final Version currentStableVersion;

    /**
     * Creates a new {@link PluginCatalogService} instance.
     *
     * @param httpClient the HTTP Client to connect to Kestra API.
     * @param icons specifies whether icons must be loaded for plugins.
     * @param communityOnly specifies whether only OSS plugins must be returned.
     */
    public PluginCatalogService(final HttpClient httpClient,
        final boolean icons,
        final boolean communityOnly) {
        this.httpClient = httpClient;
        this.icons = icons;
        this.oss = communityOnly;

        Version version = Version.of(KestraContext.getContext().getVersion());
        this.currentStableVersion = new Version(version.majorVersion(), version.minorVersion(), version.patchVersion(), null);

        // Immediately trigger an async load of plugin artifacts.
        this.isLoaded.set(true);
        this.plugins = CompletableFuture.supplyAsync(this::load);
    }

    /**
     * Resolves the version for the given artifacts.
     *
     * @param artifacts The list of artifacts to resolve.
     * @return The list of results.
     */
    public List<PluginResolutionResult> resolveVersions(List<PluginArtifact> artifacts) {
        if (ListUtils.isEmpty(artifacts)) {
            return List.of();
        }

        final Map<String, ApiPluginArtifact> pluginsByGroupAndArtifactId = getAllCompatiblePlugins().stream()
            .collect(Collectors.toMap(it -> it.groupId() + ":" + it.artifactId(), Function.identity()));

        return artifacts.stream().map(it ->
        {
            // Get all compatible versions for current artifact
            List<String> versions = Optional
                .ofNullable(pluginsByGroupAndArtifactId.get(it.groupId() + ":" + it.artifactId()))
                .map(ApiPluginArtifact::versions)
                .orElse(List.of());

            // Try to resolve the version
            String resolvedVersion = null;
            if (!versions.isEmpty()) {
                if (it.version().equalsIgnoreCase("LATEST")) {
                    resolvedVersion = versions.getFirst();
                } else {
                    resolvedVersion = versions.contains(it.version()) ? it.version() : null;
                }
            }

            // Build the PluginResolutionResult
            return new PluginResolutionResult(
                it,
                resolvedVersion,
                versions,
                resolvedVersion != null
            );
        }).toList();
    }

    public synchronized List<PluginManifest> get() {
        try {
            if (this.plugins == null && !Instant.now().isBefore(retryNotBefore)) {
                this.isLoaded.set(true);
                this.plugins = CompletableFuture.supplyAsync(this::load);
            }
            if (this.plugins == null) {
                // a previous load failed recently: keep serving the last good list until the retry delay elapses
                return loaded;
            }
            List<PluginManifest> artifacts = this.plugins.get();
            if (!artifacts.isEmpty()) {
                loaded = artifacts;
            } else if (loaded.isEmpty()) {
                // nothing cached yet and the API returned nothing: don't cache the empty result, retry later
                scheduleRetry();
            }
            if (cacheLastLoaded.plus(MAX_CACHE_DURATION).isBefore(Instant.now())) {
                if (isLoaded.compareAndSet(false, true)) {
                    // trigger an async load of plugin artifacts for refreshing local cache.
                    this.plugins = CompletableFuture.supplyAsync(this::load);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            // drop the failed future so the next call retries instead of serving the failure for MAX_CACHE_DURATION;
            // `loaded` still holds the last good list, so a transient API outage never wipes the catalog.
            scheduleRetry();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Failed to retrieve available plugins from Kestra API. Cause: Interrupted");
            } else {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Failed to retrieve available plugins from Kestra API. Cause: {}", cause.getMessage());
            }
        }
        return loaded;
    }

    private void scheduleRetry() {
        this.plugins = null;
        this.retryNotBefore = Instant.now().plus(retryDelay);
    }

    private List<PluginManifest> load() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("(Re)loading available plugin artifacts from configured Kestra API.");
            }
            List<Map<String, Object>> plugins = httpClient
                .toBlocking()
                .exchange(
                    HttpRequest.create(HttpMethod.GET, "/v1/plugins"),
                    Argument.listOf(Argument.mapOf(String.class, Object.class))
                )
                .body();

            List<PluginManifest> artifacts = plugins
                .parallelStream()
                .filter(plugin -> !plugin.get("name").equals("core"))
                .filter(plugin -> !oss || !"EE".equals(plugin.get("license")))
                .map(plugin ->
                {
                    // Get artifact
                    String groupId = "EE".equals(plugin.get("license")) ? "io.kestra.plugin.ee" : "io.kestra.plugin";
                    String artifactId = (String) plugin.get("name");

                    String icon = null;
                    if (icons) {
                        // Get icon
                        HttpResponse<String> response = httpClient
                            .toBlocking()
                            .exchange(
                                HttpRequest.create(HttpMethod.GET, "/v1/plugins/icons/" + plugin.get("group")),
                                String.class
                            );
                        icon = response.getBody()
                            .map(svg -> Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)))
                            .orElse(null);
                    }

                    return new PluginManifest(
                        (String) plugin.get("title"),
                        icon,
                        groupId,
                        artifactId
                    );
                })
                .sorted(Comparator.comparing(PluginManifest::title))
                .toList();

            if (!artifacts.isEmpty()) {
                cacheLastLoaded = Instant.now();
            }
            if (log.isDebugEnabled()) {
                log.debug("Available plugin artifacts loaded (count={})", artifacts.size());
            }
            return artifacts;
        } finally {
            isLoaded.set(false);
        }
    }

    private List<ApiPluginArtifact> getAllCompatiblePlugins() {

        MutableHttpRequest<Object> request = HttpRequest.create(
            HttpMethod.GET,
            "/v1/plugins/artifacts/core-compatibility/" + currentStableVersion
        );
        if (oss) {
            request.getParameters().add("license", "OPENSOURCE");
        }
        try {
            return httpClient
                .toBlocking()
                .exchange(request, Argument.listOf(ApiPluginArtifact.class))
                .body();
        } catch (Exception e) {
            log.debug("Failed to retrieve available plugins from Kestra API. Cause: ", e);
            return List.of();
        }
    }

    public record PluginManifest(
        String title,
        String icon,
        String groupId,
        String artifactId) {

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":LATEST";
        }
    }

    public record ApiPluginArtifact(
        String groupId,
        String artifactId,
        String license,
        List<String> versions) {
    }
}
