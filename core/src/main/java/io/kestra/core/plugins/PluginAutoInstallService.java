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
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.annotation.Value;
import io.netty.util.concurrent.FastThreadLocalThread;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects flow task/trigger types missing from the local plugin registry, maps them to their
 * catalog Maven artifact and installs them (KIP-45 "Save &amp; Fetch"). Backs the detect/install
 * endpoints, the server-side flow save hook, the first-sync migration and the boot-time install
 * of config-referenced plugins. All installs are best-effort: a failure is logged and never turns
 * a save into a hard error beyond the pre-existing validation failure.
 * <p>
 * On by default only for OSS + local-filesystem storage (the {@code server local} persona); an
 * explicit {@code kestra.plugins.auto-install.enabled} always wins over that computed default.
 */
@Singleton
@Slf4j
public class PluginAutoInstallService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final Duration DEFAULT_INSTALL_TIMEOUT = Duration.ofMinutes(2);

    // Shorter bound for the synchronous save-path hook: a flow save runs on an IO thread and a
    // namespace bulk import serializes behind the small install pool, so it must not wait the
    // full boot/migration timeout per flow.
    private static final Duration DEFAULT_SAVE_TIMEOUT = Duration.ofSeconds(30);

    /** Maven coordinates template for storage backend plugins, e.g. {@code io.kestra.storage:storage-s3:LATEST}. */
    private static final String STORAGE_ARTIFACT_TEMPLATE = "io.kestra.storage:storage-%s:LATEST";

    private final PluginCatalogService catalogService;
    private final PluginRegistry pluginRegistry;
    private final Provider<PluginInstallJobRegistry> installJobRegistry;
    private final boolean enabled;
    private final Duration installTimeout;
    private final Duration saveTimeout;
    private final Optional<String> configuredStorageType;

    @Inject
    public PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final Provider<PluginInstallJobRegistry> installJobRegistry,
        final EditionProvider editionProvider,
        @Value("${kestra.storage.type}") final Optional<String> storageType,
        @Value("${kestra.plugins.auto-install.enabled}") final Optional<Boolean> enabledProperty,
        @Value("${kestra.plugins.auto-install.install-timeout}") final Optional<Duration> installTimeoutProperty,
        @Value("${kestra.plugins.auto-install.save-timeout}") final Optional<Duration> saveTimeoutProperty) {
        // Default on only for OSS + local-filesystem storage (the "local" persona): the storage type
        // is the signal that distinguishes `server local` from a generic standalone deployment on
        // S3/GCS, which must stay inert. An explicit property value always wins, so an operator who
        // opts in on a distributed/EE deployment keeps the previous behaviour.
        this(
            catalogService,
            pluginRegistry,
            installJobRegistry,
            enabledProperty.orElseGet(
                () -> editionProvider.get() == EditionProvider.Edition.OSS
                    && storageType.map("local"::equalsIgnoreCase).orElse(false)
            ),
            installTimeoutProperty.orElse(DEFAULT_INSTALL_TIMEOUT),
            saveTimeoutProperty.orElse(DEFAULT_SAVE_TIMEOUT),
            storageType
        );
    }

    PluginAutoInstallService(
        final PluginCatalogService catalogService,
        final PluginRegistry pluginRegistry,
        final Provider<PluginInstallJobRegistry> installJobRegistry,
        final boolean enabled,
        final Duration installTimeout,
        final Duration saveTimeout,
        final Optional<String> configuredStorageType) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
        this.installJobRegistry = Objects.requireNonNull(installJobRegistry);
        this.enabled = enabled;
        this.installTimeout = Objects.requireNonNull(installTimeout);
        this.saveTimeout = Objects.requireNonNull(saveTimeout);
        this.configuredStorageType = Objects.requireNonNull(configuredStorageType);
    }

    /**
     * Returns whether the auto-install feature is enabled.
     *
     * @return {@code true} when auto-install is on — either via an explicit
     *         {@code kestra.plugins.auto-install.enabled}, or by default on OSS with local storage.
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
     * Detects the plugin types missing from the registry in the given flow YAML and resolves
     * their catalog artifacts, without installing anything. Backs {@code POST /plugins/auto-install/detect}.
     *
     * @param flowYaml the YAML source of the flow.
     * @return the detection result: feature flag, missing type FQCNs and their catalog artifacts.
     */
    public PluginAutoInstallDetectResult detect(final String flowYaml) {
        if (!enabled) {
            return new PluginAutoInstallDetectResult(false, Set.of(), List.of());
        }

        Set<String> missingTypes = findMissingTypes(flowYaml);
        List<PluginArtifact> artifacts = missingTypes.stream()
            .map(this::findArtifactForType)
            .flatMap(Optional::stream)
            .distinct()
            .toList();

        return new PluginAutoInstallDetectResult(true, missingTypes, artifacts);
    }

    /**
     * Returns whether the given artifact is provided by the plugin catalog — the install
     * endpoint's allowlist, so a caller can never make the server class-load an arbitrary
     * Maven coordinate.
     *
     * @param artifact the artifact to check.
     * @return {@code true} when a catalog manifest matches the artifact's groupId and artifactId.
     */
    public boolean isFromCatalog(final PluginArtifact artifact) {
        return artifact != null && catalogService.get().stream()
            .anyMatch(
                manifest -> manifest.groupId().equals(artifact.groupId())
                    && manifest.artifactId().equals(artifact.artifactId())
            );
    }

    /**
     * Detects the missing plugin types in the given flow YAML and installs their catalog
     * artifacts synchronously with a bounded wait — the server-side flow save hook, so every
     * entry point (import, bulk update, CLI, EE) gets missing plugins before validation.
     *
     * @param flowYaml the YAML source of the flow being saved.
     */
    public void installMissingPlugins(final String flowYaml) {
        if (!enabled) {
            return;
        }
        Set<String> missingTypes = findMissingTypes(flowYaml);
        if (missingTypes.isEmpty()) {
            return;
        }
        // Never block a Netty event loop: the catalog HTTP client needs those very event loops,
        // so waiting here would deadlock the lookup until its read timeout. The save proceeds and
        // a not-yet-installed type surfaces as the pre-existing validation error.
        if (Thread.currentThread() instanceof FastThreadLocalThread) {
            log.debug("Called from an event-loop thread: installing plugins for missing types {} in the background.", missingTypes);
            Thread.startVirtualThread(() -> installMissingTypes(missingTypes, saveTimeout));
            return;
        }
        installMissingTypes(missingTypes, saveTimeout);
    }

    /**
     * Bulk variant of {@link #installMissingPlugins(String)}, used by the first-sync migration:
     * resolves the catalog artifacts for the given missing type FQCNs and installs the
     * deduplicated set synchronously with a bounded wait.
     *
     * @param missingTypes the plugin type FQCNs missing from the local registry.
     */
    public void installMissingTypes(final Set<String> missingTypes) {
        installMissingTypes(missingTypes, installTimeout);
    }

    private void installMissingTypes(final Set<String> missingTypes, final Duration timeout) {
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

        installArtifacts(artifacts, missingTypes, timeout);
    }

    /**
     * Installs the config-referenced storage backend plugin ({@code kestra.storage.type}) when it
     * is missing, so a slim distribution configured for e.g. S3 does not fail startup. Storage
     * plugins follow the fixed {@code io.kestra.storage:storage-<id>} convention, no catalog
     * lookup involved. Must run after the external plugins directory is registered and before the
     * {@code StorageInterface} singleton is first resolved.
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
                    Set.of(type),
                    installTimeout
                )
            );
    }

    private boolean isStorageTypeRegistered(final String type) {
        return pluginRegistry.plugins().stream()
            .flatMap(plugin -> plugin.getStorages().stream())
            .anyMatch(cls -> Plugin.getId(cls).map(type::equals).orElse(false));
    }

    private void installArtifacts(final List<PluginArtifact> artifacts, final Set<String> missingTypes, final Duration timeout) {
        try {
            PluginInstallJobRegistry registry = installJobRegistry.get();
            UUID jobId = registry.submit(artifacts);
            Optional<PluginInstallJob> job = registry.awaitTerminal(jobId, timeout);
            if (job.isPresent() && PluginInstallJob.Status.SUCCEEDED == job.get().status()) {
                log.info("Auto-installed plugin artifacts {} for missing types {}.", artifacts, missingTypes);
            } else {
                log.warn(
                    "Plugin auto-install did not succeed within {} for artifacts {}: {}.",
                    timeout,
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
            // Only package-shaped FQCNs are plugin types: `type:` keys are also used by flow
            // inputs (`type: STRING`), retry blocks (`type: constant`), etc.
            if (typeValue instanceof String type && type.indexOf('.') >= 0) {
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
