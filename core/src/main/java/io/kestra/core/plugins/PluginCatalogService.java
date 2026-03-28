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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.ExecutorsUtils;
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

    private final HttpClient httpClient;
    private final ExecutorService iconLoaderExecutor;

    private CompletableFuture<List<PluginManifest>> plugins;

    private List<PluginManifest> loaded = List.of();

    private Instant cacheLastLoaded = Instant.now();
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
     * @param executorsUtils the {@link ExecutorsUtils} for creating thread pools.
     */
    public PluginCatalogService(final HttpClient httpClient,
        final boolean icons,
        final boolean communityOnly,
        final ExecutorsUtils executorsUtils) {
        this.httpClient = httpClient;
        this.icons = icons;
        this.oss = communityOnly;
        if (icons) {
            int maxAsyncThreads = Math.max(4, executorsUtils.getAllocatedCpuCores());
            this.iconLoaderExecutor = executorsUtils.maxCachedThreadPool(maxAsyncThreads, "api-plugin-catalog");
        } else {
            this.iconLoaderExecutor = null;
        }

        Version version = Version.of(KestraContext.getContext().getVersion());
        this.currentStableVersion = new Version(version.majorVersion(), version.minorVersion(), version.patchVersion(), null);
        // Loading is deferred to the first get() call to avoid blocking HTTP calls at startup.
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
            if (this.plugins == null) {
                this.isLoaded.set(true);
                this.plugins = CompletableFuture.supplyAsync(this::load);
            }
            List<PluginManifest> artifacts = this.plugins.get();
            if (!artifacts.isEmpty()) {
                loaded = artifacts;
            }
            if (cacheLastLoaded.plus(MAX_CACHE_DURATION).isBefore(Instant.now())) {
                if (isLoaded.compareAndSet(false, true)) {
                    // trigger an async load of plugin artifacts for refreshing local cache.
                    this.plugins = CompletableFuture.supplyAsync(this::load);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
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

            List<Map<String, Object>> filteredPlugins = plugins
                .stream()
                .filter(plugin -> !plugin.get("name").equals("core"))
                .filter(plugin -> !oss || !"EE".equals(plugin.get("license")))
                .toList();

            // Load icons in parallel using a dedicated executor to avoid saturating the ForkJoinPool.
            Map<String, String> iconsByGroup = Map.of();
            if (icons && iconLoaderExecutor != null) {
                List<String> groups = filteredPlugins.stream()
                    .map(plugin -> (String) plugin.get("group"))
                    .distinct()
                    .toList();

                List<CompletableFuture<Map.Entry<String, String>>> iconFutures = groups.stream()
                    .map(group -> CompletableFuture.supplyAsync(() -> {
                        try {
                            HttpResponse<String> response = httpClient
                                .toBlocking()
                                .exchange(
                                    HttpRequest.create(HttpMethod.GET, "/v1/plugins/icons/" + group),
                                    String.class
                                );
                            String icon = response.getBody()
                                .map(svg -> Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)))
                                .orElse(null);
                            return Map.entry(group, icon != null ? icon : "");
                        } catch (Exception e) {
                            log.debug("Failed to load icon for plugin group '{}': {}", group, e.getMessage());
                            return Map.entry(group, "");
                        }
                    }, iconLoaderExecutor))
                    .toList();

                iconsByGroup = iconFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(entry -> !entry.getValue().isEmpty())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            Map<String, String> finalIconsByGroup = iconsByGroup;
            List<PluginManifest> artifacts = filteredPlugins.stream()
                .map(plugin -> {
                    String groupId = "EE".equals(plugin.get("license")) ? "io.kestra.plugin.ee" : "io.kestra.plugin";
                    String artifactId = (String) plugin.get("name");
                    String icon = finalIconsByGroup.getOrDefault((String) plugin.get("group"), null);
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
