package io.kestra.core.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.SchemaType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Version;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the pre-baked plugin schema bundle (built by {@code PluginsSchemaCommand}) and merges it
 * into locally-generated schemas via {@link PluginSchemaBundleMerger}, so the editor can complete
 * plugin types that are not installed locally. Installed plugins always take precedence.
 *
 * <p>
 * The bundle source is resolved once at construction, in priority order (see
 * {@link #resolveBundleSource}): an explicit local file ({@code kestra.plugins.schema-bundle-path}),
 * the {@code /plugins-schema.json} classpath resource embedded by release CI, then a self-hosted
 * URL template ({@code kestra.plugins.schema-bundle-url-template}). When none resolves the service
 * is a no-op — the expected state on a plain {@code ./gradlew build} or a {@code develop} build.
 * The bundle is immutable per release, so it is loaded once on first use and cached forever; a
 * failed remote fetch is retried with a backoff.
 */
@Singleton
@Slf4j
public class PluginSchemaBundleService {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    // The bundle baked into the Kestra JAR as a classpath resource (embedded by release CI).
    private static final String CLASSPATH_BUNDLE = "/plugins-schema.json";

    private final String resolvedBundleUrl;

    private volatile Bundle bundle;
    private volatile Instant lastFailureAt;

    public PluginSchemaBundleService(
        @Nullable @Value("${kestra.plugins.schema-bundle-path:}") String bundlePath,
        @Nullable @Value("${kestra.plugins.schema-bundle-url-template:}") String bundleUrlTemplate) {
        this.resolvedBundleUrl = resolveBundleSource(
            bundlePath,
            bundleUrlTemplate,
            PluginSchemaBundleService.class.getResource(CLASSPATH_BUNDLE),
            () -> KestraContext.getContext().getVersion()
        );
    }

    /**
     * Resolves the single bundle source URL, in priority order: explicit local file, then the
     * JAR-bundled classpath resource, then the remote URL template. Returns {@code ""} when none
     * applies (the service is then a no-op). Package-private and side-effect-free so the
     * resolution order can be unit-tested.
     *
     * @param bundlePath value of {@code kestra.plugins.schema-bundle-path}, may be {@code null}/blank
     * @param bundleUrlTemplate value of {@code kestra.plugins.schema-bundle-url-template}, may be {@code null}/blank
     * @param classpathBundle the {@link #CLASSPATH_BUNDLE} resource URL, or {@code null} when not bundled
     * @param versionSupplier supplies the running Kestra version, read only for the URL-template branch
     * @return the resolved source as a URL string, or {@code ""} when the service should be a no-op
     */
    static String resolveBundleSource(
        @Nullable String bundlePath,
        @Nullable String bundleUrlTemplate,
        @Nullable URL classpathBundle,
        Supplier<String> versionSupplier) {
        // Explicit local-file override — highest priority so a developer (or plugin-devtools) can
        // point at a full-catalog bundle and always win over the JAR-bundled default.
        if (bundlePath != null && !bundlePath.isBlank()) {
            Path path = Path.of(bundlePath.trim());
            if (Files.isRegularFile(path)) {
                return path.toUri().toString();
            }
            log.warn("Configured plugin schema bundle path '{}' is not a readable file; ignoring it.", bundlePath);
        }

        // Bundle shipped inside the Kestra JAR — no network access, works offline / air-gapped.
        if (classpathBundle != null) {
            return classpathBundle.toString();
        }

        // Remote URL template fallback, keyed by the stripped stable version.
        if (bundleUrlTemplate != null && !bundleUrlTemplate.isBlank()) {
            Version version = Version.of(versionSupplier.get());
            String stable = new Version(version.majorVersion(), version.minorVersion(), version.patchVersion(), null).toString();
            return bundleUrlTemplate.replace("{version}", stable);
        }

        return "";
    }

    /**
     * Returns whether a bundle source (local file, JAR-bundled resource, or remote URL) was resolved
     * and the service is therefore active.
     */
    public boolean isEnabled() {
        return !resolvedBundleUrl.isEmpty();
    }

    /**
     * Returns a fingerprint of the loaded bundle, usable in an HTTP ETag: it changes when a
     * different bundle (or none) is loaded, and is stable otherwise.
     */
    public String fingerprint() {
        Bundle loaded = bundle;
        return loaded == null ? "none" : Integer.toHexString(resolvedBundleUrl.hashCode()) + "-" + loaded.definitions().size();
    }

    /**
     * Returns whether the loaded bundle carries a definition for the given type FQCN, i.e. the
     * type exists in the plugin catalog this release was built against.
     */
    public boolean containsType(String fqcn) {
        return isEnabled() && fqcn != null && getBundle().definitions().has(fqcn);
    }

    /**
     * Returns a copy of {@code localSchema} enriched with a lightweight definition and a
     * {@code $ref} branch for every catalog subtype not installed locally — see
     * {@link PluginSchemaBundleMerger}. When the service is disabled or no bundle could be loaded,
     * the original {@code localSchema} is returned unchanged.
     *
     * @param type the schema type to look up in the bundle
     * @param localSchema the locally-generated schema (not modified)
     * @return merged schema, or the original when nothing to merge
     */
    public Map<String, Object> mergeWithBundle(SchemaType type, Map<String, Object> localSchema) {
        if (!isEnabled()) {
            return localSchema;
        }

        Bundle loaded = getBundle();
        if (loaded.isEmpty() || loaded.definitions().isEmpty()) {
            return localSchema;
        }

        ObjectNode mutable = MAPPER.convertValue(localSchema, ObjectNode.class);
        PluginSchemaBundleMerger.mergeLightweightSubtypes(mutable, loaded.definitions(), loaded.roots());
        return MAPPER.convertValue(mutable, MAP_TYPE);
    }

    // The bundle is immutable per release: load it once on first use, keep it forever. On failure
    // (only really possible for the remote-URL escape hatch) retry after a backoff.
    private Bundle getBundle() {
        Bundle loaded = bundle;
        if (loaded != null) {
            return loaded;
        }
        synchronized (this) {
            if (bundle != null) {
                return bundle;
            }
            Instant failedAt = lastFailureAt;
            if (failedAt != null && failedAt.plus(FAILURE_BACKOFF).isAfter(Instant.now())) {
                return Bundle.EMPTY;
            }
            Bundle result = load();
            if (result.isEmpty()) {
                lastFailureAt = Instant.now();
            } else {
                bundle = result;
            }
            return result;
        }
    }

    private Bundle load() {
        try (InputStream in = openBundleStream()) {
            JsonNode root = MAPPER.readTree(in);
            ObjectNode definitions = root.get("definitions") instanceof ObjectNode defs ? defs : JsonNodeFactory.instance.objectNode();

            Map<SchemaType, String> roots = new EnumMap<>(SchemaType.class);
            if (root.get("roots") instanceof ObjectNode rootsNode) {
                rootsNode.properties().forEach(entry ->
                {
                    try {
                        roots.put(SchemaType.fromString(entry.getKey()), entry.getValue().asText());
                    } catch (Exception e) {
                        log.debug("Ignoring unknown schema type '{}' in bundle", entry.getKey());
                    }
                });
            }

            log.debug("Plugin schema bundle loaded from '{}' ({} shared definitions, {} root types)", resolvedBundleUrl, definitions.size(), roots.size());
            return new Bundle(definitions, roots);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Could not load plugin schema bundle from '{}': {}", resolvedBundleUrl, e.getMessage());
            return Bundle.EMPTY;
        }
    }

    // http(s) goes through the JDK HttpClient with timeouts; file: and jar: (local file /
    // classpath resource, the normal sources) are plain stream reads HttpClient cannot handle.
    private InputStream openBundleStream() throws IOException, InterruptedException {
        URI uri = URI.create(resolvedBundleUrl);
        if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(FETCH_TIMEOUT).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Fetching the plugin schema bundle from '%s' returned HTTP status %d.".formatted(resolvedBundleUrl, response.statusCode()));
            }
            return response.body();
        }
        return uri.toURL().openStream();
    }

    /** The bundle's shared {@code definitions} pool plus each {@link SchemaType}'s root {@code $ref} into it. */
    private record Bundle(ObjectNode definitions, Map<SchemaType, String> roots) {
        static final Bundle EMPTY = new Bundle(JsonNodeFactory.instance.objectNode(), Map.of());

        boolean isEmpty() {
            return roots.isEmpty();
        }
    }
}
