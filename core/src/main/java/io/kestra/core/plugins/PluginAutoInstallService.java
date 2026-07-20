package io.kestra.core.plugins;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.JsonSchemaCache;
import io.kestra.core.models.ServerType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that automatically downloads and installs missing plugins when a flow is saved.
 * <p>
 * This is the core "Save &amp; Fetch" mechanism described in KIP-45: when a user saves a flow that
 * references a plugin not yet present in the plugin registry, this service:
 * <ol>
 * <li>Parses the flow YAML to extract all task/trigger type FQCNs.</li>
 * <li>Identifies which ones are absent from the current {@link PluginRegistry}.</li>
 * <li>Maps each missing FQCN to its Maven artifact via the {@link PluginCatalogService}.</li>
 * <li>Resolves each artifact's version to the highest one compatible with this Kestra instance.</li>
 * <li>Downloads and installs the missing artifacts via {@link PluginManager}.</li>
 * <li>Clears the {@link JsonSchemaCache} so the new plugins are immediately reflected in the schema.</li>
 * </ol>
 * <p>
 * Works transparently with both {@code LocalPluginManager} (OSS standalone — installs to local disk)
 * and {@code RemotePluginManager} (EE distributed — uploads to internal storage and notifies the cluster).
 * <p>
 * Feature gating: on by default only for OSS standalone (the KIP-45 target persona). Everywhere else
 * (OSS distributed, Enterprise Edition) it is off unless {@code kestra.plugins.auto-install.enabled}
 * is set explicitly — an explicit value always wins over the computed default.
 */
@Singleton
@Slf4j
public class PluginAutoInstallService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PluginCatalogService catalogService;
    private final PluginManager pluginManager;
    private final PluginRegistry pluginRegistry;
    private final JsonSchemaCache jsonSchemaCache;
    private final boolean enabled;

    @Inject
    public PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginManager pluginManager,
        final PluginRegistry pluginRegistry,
        final JsonSchemaCache jsonSchemaCache,
        final EditionProvider editionProvider,
        @Value("${kestra.plugins.auto-install.enabled}") final Optional<Boolean> enabledProperty) {
        // Default on only for OSS standalone; an explicit property value always wins, so an operator
        // who opts in on a distributed/EE deployment keeps the previous behaviour.
        this(
            catalogService,
            pluginManager,
            pluginRegistry,
            jsonSchemaCache,
            enabledProperty.orElseGet(
                () -> editionProvider.get() == EditionProvider.Edition.OSS
                    && KestraContext.getContext().getServerType() == ServerType.STANDALONE
            )
        );
    }

    PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginManager pluginManager,
        final PluginRegistry pluginRegistry,
        final JsonSchemaCache jsonSchemaCache,
        final boolean enabled) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.pluginManager = Objects.requireNonNull(pluginManager);
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
        this.jsonSchemaCache = Objects.requireNonNull(jsonSchemaCache);
        this.enabled = enabled;
    }

    /**
     * Returns whether the auto-install feature is enabled.
     *
     * @return {@code true} when auto-install is on — either via an explicit
     *         {@code kestra.plugins.auto-install.enabled}, or by default on OSS standalone.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Scans the given flow YAML for task/trigger types that are not yet registered in the plugin
     * registry, resolves their Maven artifacts from the catalog, downloads and installs them, then
     * clears the JSON schema cache so the new plugins are immediately visible.
     * <p>
     * This method is a no-op when the feature is disabled or when all referenced types are already
     * installed. Failures to map a type to a catalog artifact are logged as warnings and do not
     * propagate — the subsequent flow-save attempt will surface the error normally.
     *
     * @param flowYaml the YAML source of the flow to save.
     * @return the list of plugin artifacts that were installed, or an empty list if nothing changed.
     */
    public List<PluginArtifact> installMissingPlugins(final String flowYaml) {
        if (!enabled) {
            return List.of();
        }

        Set<String> missingTypes = findMissingTypes(flowYaml);
        if (missingTypes.isEmpty()) {
            return List.of();
        }

        log.info("Detected missing plugin types on flow save, attempting auto-install: {}", missingTypes);

        List<PluginArtifact> toInstall = missingTypes.stream()
            .map(this::findArtifactForType)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .toList();

        if (toInstall.isEmpty()) {
            log.warn(
                "Could not map missing plugin types to catalog artifacts " +
                    "(network unavailable or types not published): {}",
                missingTypes
            );
            return List.of();
        }

        List<PluginArtifact> resolved = resolveCompatibleVersions(toInstall);
        if (resolved.isEmpty()) {
            log.warn("Could not resolve a Kestra-compatible version for any missing plugin artifact: {}", toInstall);
            return List.of();
        }

        List<PluginArtifact> installed = pluginManager.install(resolved, List.of(), true, null);
        jsonSchemaCache.clear();
        log.info("Auto-installed {} plugin artifact(s): {}", installed.size(), installed);
        return installed;
    }

    /**
     * Resolves each {@code LATEST}-versioned artifact to the highest version that is compatible with the
     * current Kestra core version, using {@link PluginCatalogService#resolveVersions(List)}. Artifacts for
     * which no compatible version is found are dropped (and logged as a warning) rather than falling back
     * to the newest version overall, which could be incompatible with this instance.
     *
     * @param artifacts the artifacts to resolve, with a placeholder {@code LATEST} version.
     * @return the artifacts with their version resolved to a Kestra-compatible one.
     */
    private List<PluginArtifact> resolveCompatibleVersions(final List<PluginArtifact> artifacts) {
        return catalogService.resolveVersions(artifacts).stream()
            .peek(result ->
            {
                if (!result.resolved()) {
                    log.warn(
                        "No Kestra-compatible version found for plugin artifact '{}' (available versions: {})",
                        result.artifact(), result.versions()
                    );
                }
            })
            .filter(PluginResolutionResult::resolved)
            .map(result -> result.artifact().toBuilder().version(result.version()).build())
            .toList();
    }

    /**
     * Returns all task/trigger type FQCNs referenced in the given flow YAML that are not currently
     * registered in the plugin registry.
     *
     * @param flowYaml the YAML source of the flow.
     * @return a set of missing type FQCNs; empty if the YAML cannot be parsed or all types are known.
     */
    public Set<String> findMissingTypes(final String flowYaml) {
        Map<String, Object> rawFlow;
        try {
            rawFlow = JacksonMapper.ofYaml().readValue(flowYaml, MAP_TYPE);
        } catch (Exception e) {
            log.debug("Could not parse flow YAML for plugin type extraction", e);
            return Set.of();
        }

        Set<String> allTypes = new HashSet<>();
        collectTypes(rawFlow, allTypes);

        return allTypes.stream()
            .filter(type -> pluginRegistry.findClassByIdentifier(type) == null)
            .collect(Collectors.toSet());
    }

    /**
     * Finds the Maven artifact that provides the given plugin type FQCN, using the plugin catalog.
     * <p>
     * Uses longest-prefix matching on the plugin's Java package group so that sub-packages
     * (e.g. {@code io.kestra.plugin.scripts.python}) are preferred over parent packages
     * (e.g. {@code io.kestra.plugin.scripts}).
     *
     * @param fqcn the fully-qualified class name of the task or trigger type.
     * @return the corresponding {@link PluginArtifact} ready for installation, or empty if not found.
     */
    public Optional<PluginArtifact> findArtifactForType(final String fqcn) {
        return catalogService.get().stream()
            .filter(manifest -> manifest.group() != null && fqcn.startsWith(manifest.group() + "."))
            .max(Comparator.comparingInt(m -> m.group().length()))
            .map(manifest -> PluginArtifact.fromCoordinates(manifest.toString()));
    }

    @SuppressWarnings("unchecked")
    private void collectTypes(final Object node, final Set<String> types) {
        if (node instanceof Map<?, ?> map) {
            Object typeValue = ((Map<String, Object>) map).get("type");
            if (typeValue instanceof String type && !type.isBlank()) {
                types.add(type);
            }
            for (Object value : ((Map<String, Object>) map).values()) {
                collectTypes(value, types);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectTypes(item, types);
            }
        }
    }
}
