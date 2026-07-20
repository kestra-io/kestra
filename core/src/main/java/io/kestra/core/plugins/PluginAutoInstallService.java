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
import io.kestra.core.models.ServerType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that detects flow task/trigger types missing from the local plugin registry and maps
 * them to their catalog Maven artifact.
 * <p>
 * This backs the KIP-45 "Save &amp; Fetch" flow, which is frontend-orchestrated: the editor calls
 * {@code POST /api/v1/plugins/auto-install/detect} (backed by {@link #findMissingTypes} and
 * {@link #findArtifactForType}) to learn what's missing before save, then
 * {@code POST /api/v1/plugins/install} (backed by {@link PluginInstallJobRegistry}) to actually
 * download and install the artifacts. This service itself never installs anything.
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
    private final PluginRegistry pluginRegistry;
    private final boolean enabled;

    @Inject
    public PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final EditionProvider editionProvider,
        @Value("${kestra.plugins.auto-install.enabled}") final Optional<Boolean> enabledProperty) {
        // Default on only for OSS standalone; an explicit property value always wins, so an operator
        // who opts in on a distributed/EE deployment keeps the previous behaviour.
        this(
            catalogService,
            pluginRegistry,
            enabledProperty.orElseGet(
                () -> editionProvider.get() == EditionProvider.Edition.OSS
                    && KestraContext.getContext().getServerType() == ServerType.STANDALONE
            )
        );
    }

    PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final boolean enabled) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
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
