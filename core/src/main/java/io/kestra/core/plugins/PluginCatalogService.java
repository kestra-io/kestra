package io.kestra.core.plugins;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.annotation.Nullable;
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

    private final Map<String, Optional<byte[]>> iconBytesByGroup = new ConcurrentHashMap<>();

    private final boolean icons;
    private final boolean oss;
    private final PluginSchemaBundleService schemaBundleService;

    /**
     * Creates a new {@link PluginCatalogService} instance backed by the hosted catalog only, with
     * no local entries from the plugin schema bundle.
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
        this(httpClient, icons, communityOnly, executorsUtils, null);
    }

    /**
     * Creates a new {@link PluginCatalogService} instance.
     *
     * @param httpClient the HTTP Client to connect to Kestra API.
     * @param icons specifies whether icons must be loaded for plugins.
     * @param communityOnly specifies whether only OSS plugins must be returned.
     * @param executorsUtils the {@link ExecutorsUtils} for creating thread pools.
     * @param schemaBundleService the plugin schema bundle, whose entries extend the hosted catalog
     *        with the plugins the bundle was built from; may be {@code null}.
     */
    public PluginCatalogService(final HttpClient httpClient,
        final boolean icons,
        final boolean communityOnly,
        final ExecutorsUtils executorsUtils,
        @Nullable final PluginSchemaBundleService schemaBundleService) {
        this.httpClient = httpClient;
        this.icons = icons;
        this.oss = communityOnly;
        this.schemaBundleService = schemaBundleService;
        // Loading is deferred to the first get() call to avoid blocking HTTP calls at startup.
    }

    // Resolved on each call, not at construction time: this bean can be instantiated during
    // database migrations, before the KestraContext static holder is initialized.
    private static Version currentStableVersion() {
        Version version = Version.of(KestraContext.getContext().getVersion());
        return new Version(version.majorVersion(), version.minorVersion(), version.patchVersion(), null);
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
                return withBundleEntries(loaded);
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
        return withBundleEntries(loaded);
    }

    /**
     * Extends the hosted catalog with the entries carried by the plugin schema bundle, so a plugin
     * the hosted catalog does not list — a private or in-house one, built into the bundle from a
     * local plugin folder — resolves and installs like any other. Hosted entries win on conflict:
     * they carry the license and version metadata the API is authoritative for.
     *
     * @param hosted the manifests retrieved from the Kestra API.
     * @return the merged manifests.
     */
    private List<PluginManifest> withBundleEntries(final List<PluginManifest> hosted) {
        if (schemaBundleService == null) {
            return hosted;
        }

        List<PluginManifest> fromBundle = schemaBundleService.catalogEntries();
        if (fromBundle.isEmpty()) {
            return hosted;
        }

        Set<String> known = hosted.stream()
            .map(manifest -> manifest.groupId() + ":" + manifest.artifactId())
            .collect(Collectors.toSet());

        List<PluginManifest> merged = new ArrayList<>(hosted);
        fromBundle.stream()
            .filter(manifest -> manifest.groupId() != null && manifest.artifactId() != null && manifest.group() != null)
            .filter(manifest -> !known.contains(manifest.groupId() + ":" + manifest.artifactId()))
            .forEach(merged::add);

        return List.copyOf(merged);
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

            List<Map<String, Object>> filteredPlugins = plugins
                .stream()
                .filter(plugin -> !plugin.get("name").equals("core"))
                .filter(plugin -> !oss || !"EE".equals(plugin.get("license")))
                .toList();

            List<PluginManifest> artifacts = filteredPlugins.stream()
                .map(plugin ->
                {
                    String groupId = "EE".equals(plugin.get("license")) ? "io.kestra.plugin.ee" : "io.kestra.plugin";
                    String artifactId = (String) plugin.get("name");
                    return new PluginManifest(
                        (String) plugin.get("title"),
                        groupId,
                        artifactId,
                        (String) plugin.get("group")
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

    public Optional<byte[]> icon(final String groupId, final String artifactId) {
        if (!icons) {
            return Optional.empty();
        }

        String group = get().stream()
            .filter(manifest -> groupId.equals(manifest.groupId()) && artifactId.equals(manifest.artifactId()))
            .map(PluginManifest::group)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        return icon(group);
    }

    public Optional<byte[]> icon(final String group) {
        if (!icons || group == null) {
            return Optional.empty();
        }

        Optional<byte[]> cached = iconBytesByGroup.get(group);
        if (cached != null) {
            return cached;
        }
        Optional<byte[]> fetched = fetchGroupIcon(group);
        if (fetched.isPresent()) {
            iconBytesByGroup.put(group, fetched);
        }
        return fetched;
    }

    private Optional<byte[]> fetchGroupIcon(final String group) {
        try {
            return httpClient
                .toBlocking()
                .exchange(HttpRequest.create(HttpMethod.GET, "/v1/plugins/icons/" + group), String.class)
                .getBody()
                .filter(svg -> !svg.isBlank())
                .map(svg -> svg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("Failed to load icon for plugin group '{}': {}", group, e.getMessage());
            return Optional.empty();
        }
    }

    private List<ApiPluginArtifact> getAllCompatiblePlugins() {

        MutableHttpRequest<Object> request = HttpRequest.create(
            HttpMethod.GET,
            "/v1/plugins/artifacts/core-compatibility/" + currentStableVersion()
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

    /**
     * @param title human-readable plugin title
     * @param groupId Maven group id of the plugin artifact
     * @param artifactId Maven artifact id of the plugin artifact
     * @param group Java package group of the plugin (e.g. {@code io.kestra.plugin.aws}), used for
     *        type-to-artifact matching; may be {@code null}
     */
    public record PluginManifest(
        String title,
        String groupId,
        String artifactId,
        String group) {

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
