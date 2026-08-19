package io.kestra.core.plugins;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.ServerType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that detects flow task/trigger types missing from the local plugin registry and maps
 * them to their catalog Maven artifact.
 * <p>
 * This backs the KIP-45 "Save &amp; Fetch" flow in two ways:
 * <ul>
 * <li><b>Frontend-orchestrated</b>: the editor calls
 * {@code POST /api/v1/plugins/auto-install/detect} (backed by {@link #findMissingTypes} and
 * {@link #findArtifactForType}) to learn what's missing before save, then
 * {@code POST /api/v1/plugins/install} (backed by {@link PluginInstallJobRegistry}) to actually
 * download and install the artifacts.</li>
 * <li><b>Server-side</b>: {@link #installMissingPlugins(String)} is invoked from the flow save
 * path ({@code FlowService}) so every non-UI entry point (flow import, namespace bulk update,
 * CLI, kestractl, EE) transparently installs missing plugins before validation. Installation is
 * best-effort: a failure is logged as a warning and never turns a save into a hard error beyond
 * the pre-existing validation failure.</li>
 * <li><b>First-sync / boot</b>: {@link #installMissingTypes(Set)} backs the 1.3 → 2.0 migration
 * that crawls every existing flow once, and {@link #installMissingConfiguredPlugins()} installs
 * config-referenced plugins (storage backend) at server startup, before the corresponding
 * services initialize.</li>
 * </ul>
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

    private static final Duration DEFAULT_INSTALL_TIMEOUT = Duration.ofMinutes(2);

    /** Maven coordinates template for storage backend plugins, e.g. {@code io.kestra.storage:storage-s3:LATEST}. */
    private static final String STORAGE_ARTIFACT_TEMPLATE = "io.kestra.storage:storage-%s:LATEST";

    private final PluginCatalogService catalogService;
    private final PluginRegistry pluginRegistry;
    private final Provider<PluginInstallJobRegistry> installJobRegistry;
    private final boolean enabled;
    private final Duration installTimeout;
    private final Optional<String> configuredStorageType;

    @Inject
    public PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final Provider<PluginInstallJobRegistry> installJobRegistry,
        final EditionProvider editionProvider,
        @Value("${kestra.server-type}") final Optional<ServerType> serverType,
        @Value("${kestra.storage.type}") final Optional<String> storageType,
        @Value("${kestra.plugins.auto-install.enabled}") final Optional<Boolean> enabledProperty,
        @Value("${kestra.plugins.auto-install.install-timeout}") final Optional<Duration> installTimeoutProperty) {
        // Default on only for OSS standalone; an explicit property value always wins, so an operator
        // who opts in on a distributed/EE deployment keeps the previous behaviour. The server type is
        // read from the property (not KestraContext) because this bean can be instantiated during
        // database migrations, before the KestraContext static holder is initialized; absent defaults
        // to STANDALONE, mirroring KestraContext.Initializer.
        this(
            catalogService,
            pluginRegistry,
            installJobRegistry,
            enabledProperty.orElseGet(
                () -> editionProvider.get() == EditionProvider.Edition.OSS
                    && ServerType.STANDALONE == serverType.orElse(ServerType.STANDALONE)
            ),
            installTimeoutProperty.orElse(DEFAULT_INSTALL_TIMEOUT),
            storageType
        );
    }

    PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final Provider<PluginInstallJobRegistry> installJobRegistry,
        final boolean enabled,
        final Duration installTimeout,
        final Optional<String> configuredStorageType) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
        this.installJobRegistry = Objects.requireNonNull(installJobRegistry);
        this.enabled = enabled;
        this.installTimeout = Objects.requireNonNull(installTimeout);
        this.configuredStorageType = Objects.requireNonNull(configuredStorageType);
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

    /**
     * Detects the plugin types missing from the registry in the given flow YAML, resolves their
     * catalog artifacts, and installs the deduplicated set synchronously with a bounded wait.
     * <p>
     * This is the server-side hook used by the flow save path so that flows saved through any
     * entry point (flow import, namespace bulk update, CLI, kestractl, EE) get their missing
     * plugins installed before validation. It is best-effort by design: any failure — no catalog
     * match, download error, timeout — is logged as a warning and never propagated, so a save
     * fails only through the pre-existing type validation, exactly as it would without this hook.
     *
     * @param flowYaml the YAML source of the flow being saved.
     */
    public void installMissingPlugins(final String flowYaml) {
        if (!enabled) {
            return;
        }
        installMissingTypes(findMissingTypes(flowYaml));
    }

    /**
     * Resolves the catalog artifacts for the given missing plugin type FQCNs and installs the
     * deduplicated set synchronously with a bounded wait.
     * <p>
     * This is the bulk variant of {@link #installMissingPlugins(String)}, reused by the
     * 1.3 → 2.0 first-sync migration which aggregates the missing types of every existing flow
     * before triggering a single install. Same best-effort semantics: any failure is logged as a
     * warning and never propagated.
     *
     * @param missingTypes the plugin type FQCNs missing from the local registry.
     */
    public void installMissingTypes(final Set<String> missingTypes) {
        if (!enabled || missingTypes.isEmpty()) {
            return;
        }

        List<PluginArtifact> artifacts = missingTypes.stream()
            .map(this::findArtifactForType)
            .flatMap(Optional::stream)
            .distinct()
            .toList();

        if (artifacts.isEmpty()) {
            log.warn("Cannot auto-install plugins for missing types {}: no matching artifact was found in the plugin catalog.", missingTypes);
            return;
        }

        installArtifacts(artifacts, missingTypes);
    }

    /**
     * Detects plugin types referenced by the resolved configuration — not by flow YAML — that are
     * missing from the local plugin registry, and installs them synchronously with a bounded wait.
     * <p>
     * This covers the {@code kestra.storage.type} storage backend, which is needed at boot before
     * the {@code StorageInterface} singleton is first resolved, so a slim distribution configured
     * for e.g. S3 does not fail startup. Storage plugins follow the fixed Maven convention
     * {@code io.kestra.storage:storage-<id>}, so no catalog lookup is involved. The other
     * config-referenced plugin kinds need no boot-time install in OSS: secret managers are
     * Enterprise Edition plugins resolved by the EE secret factory, log datastores (h2, mysql,
     * postgres) ship in the distribution itself, and file renderers are discovered per request
     * after installation.
     * <p>
     * Must be called after the external plugins directory has been registered (see
     * {@code AbstractCommand#maybeInitPlugins()}) and before the corresponding services initialize.
     * Best-effort: on failure the boot continues and fails later with the pre-existing
     * "no storage interface found" error.
     */
    public void installMissingConfiguredPlugins() {
        if (!enabled) {
            return;
        }

        configuredStorageType
            .map(type -> type.toLowerCase(Locale.ROOT))
            .filter(type -> !isStorageTypeRegistered(type))
            .ifPresent(
                type -> installArtifacts(
                    List.of(PluginArtifact.fromCoordinates(STORAGE_ARTIFACT_TEMPLATE.formatted(type))),
                    Set.of(type)
                )
            );
    }

    private boolean isStorageTypeRegistered(final String type) {
        return pluginRegistry.plugins().stream()
            .flatMap(plugin -> plugin.getStorages().stream())
            .anyMatch(cls -> Plugin.getId(cls).map(type::equals).orElse(false));
    }

    private void installArtifacts(final List<PluginArtifact> artifacts, final Set<String> missingTypes) {
        try {
            PluginInstallJobRegistry registry = installJobRegistry.get();
            UUID jobId = registry.submit(artifacts);
            Optional<PluginInstallJob> job = registry.awaitTerminal(jobId, installTimeout);
            if (job.isPresent() && PluginInstallJob.Status.SUCCEEDED == job.get().status()) {
                log.info("Auto-installed plugin artifacts {} for missing types {}.", artifacts, missingTypes);
            } else {
                log.warn(
                    "Plugin auto-install did not succeed within {} for artifacts {}: {}.",
                    installTimeout,
                    artifacts,
                    job.map(it -> it.error() != null ? it.error() : "job is still " + it.status()).orElse("job not found")
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Plugin auto-install was interrupted while installing artifacts {}.", artifacts, e);
        } catch (Exception e) {
            // Best-effort by design: an install failure must never turn a flow save into a hard error.
            log.warn("Plugin auto-install failed for artifacts {}.", artifacts, e);
        }
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
