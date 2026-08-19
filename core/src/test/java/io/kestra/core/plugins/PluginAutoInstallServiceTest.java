package io.kestra.core.plugins;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.EditionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginAutoInstallServiceTest {

    private static final Duration INSTALL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SAVE_TIMEOUT = Duration.ofSeconds(2);

    private PluginCatalogService catalogService;
    private PluginRegistry pluginRegistry;
    private PluginInstallJobRegistry installJobRegistry;

    @BeforeEach
    void setUp() {
        catalogService = mock(PluginCatalogService.class);
        pluginRegistry = mock(PluginRegistry.class);
        installJobRegistry = mock(PluginInstallJobRegistry.class);
    }

    // ─── findMissingTypes ──────────────────────────────────────────────────────

    @Test
    void shouldExtractTopLevelTaskType() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
                url: https://example.com
            """;
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.http.request.Request")).thenReturn(null);

        PluginAutoInstallService service = enabledService();

        // When
        Set<String> missing = service.findMissingTypes(yaml);

        // Then
        assertThat(missing).containsExactly("io.kestra.plugin.http.request.Request");
    }

    @Test
    void shouldExtractNestedTaskTypes() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: parallel
                type: io.kestra.plugin.core.flow.Parallel
                tasks:
                  - id: inner
                    type: io.kestra.plugin.scripts.python.Script
                    script: print("hi")
            """;
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.core.flow.Parallel")).thenReturn(null);
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.scripts.python.Script")).thenReturn(null);

        PluginAutoInstallService service = enabledService();

        // When
        Set<String> missing = service.findMissingTypes(yaml);

        // Then
        assertThat(missing).containsExactlyInAnyOrder(
            "io.kestra.plugin.core.flow.Parallel",
            "io.kestra.plugin.scripts.python.Script"
        );
    }

    @Test
    void shouldNotReturnAlreadyRegisteredTypes() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        // Simulate "already registered"
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.http.request.Request"))
            .thenReturn((Class) Object.class);

        PluginAutoInstallService service = enabledService();

        // When
        Set<String> missing = service.findMissingTypes(yaml);

        // Then
        assertThat(missing).isEmpty();
    }

    @Test
    void shouldIgnoreNonFqcnTypeValues() {
        // Given — `type:` keys that are not plugin types: flow inputs and retry blocks
        String yaml = """
            id: my-flow
            namespace: company
            inputs:
              - id: my-input
                type: STRING
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempt: 3
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);

        PluginAutoInstallService service = enabledService();

        // When
        Set<String> missing = service.findMissingTypes(yaml);

        // Then — STRING and constant are not package-shaped and must not be reported
        assertThat(missing).containsExactly("io.kestra.plugin.http.request.Request");
    }

    @Test
    void shouldReturnEmptySetForInvalidYaml() {
        // Given
        PluginAutoInstallService service = enabledService();

        // When
        Set<String> missing = service.findMissingTypes("{ invalid: yaml: [");

        // Then
        assertThat(missing).isEmpty();
    }

    // ─── findArtifactForType ──────────────────────────────────────────────────

    @Test
    void shouldMatchArtifactByExactGroup() {
        // Given
        when(catalogService.get()).thenReturn(
            List.of(
                manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http")
            )
        );
        PluginAutoInstallService service = enabledService();

        // When
        Optional<PluginArtifact> result = service.findArtifactForType("io.kestra.plugin.http.request.Request");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().groupId()).isEqualTo("io.kestra.plugin");
        assertThat(result.get().artifactId()).isEqualTo("plugin-http");
    }

    @Test
    void shouldPreferLongestGroupPrefixMatch() {
        // Given — plugin-scripts owns "io.kestra.plugin.scripts" and
        //          plugin-script-python owns the deeper "io.kestra.plugin.scripts.python"
        when(catalogService.get()).thenReturn(
            List.of(
                manifest("io.kestra.plugin", "plugin-scripts", "io.kestra.plugin.scripts"),
                manifest("io.kestra.plugin", "plugin-script-python", "io.kestra.plugin.scripts.python")
            )
        );
        PluginAutoInstallService service = enabledService();

        // When
        Optional<PluginArtifact> result = service.findArtifactForType("io.kestra.plugin.scripts.python.Script");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().artifactId()).isEqualTo("plugin-script-python");
    }

    @Test
    void shouldReturnEmptyWhenNoCatalogEntry() {
        // Given
        when(catalogService.get()).thenReturn(List.of());
        PluginAutoInstallService service = enabledService();

        // When
        Optional<PluginArtifact> result = service.findArtifactForType("io.kestra.plugin.unknown.Task");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldBeNoOpWhenDisabled() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        PluginAutoInstallService service = disabledService();

        // When / Then — service state is independent of findMissingTypes, which only depends on the registry
        assertThat(service.isEnabled()).isFalse();
    }

    // ─── detect / isFromCatalog ───────────────────────────────────────────────

    @Test
    void shouldReturnEmptyDetectionWhenDisabled() {
        // Given
        PluginAutoInstallService service = disabledService();

        // When
        PluginAutoInstallDetectResult result = service.detect("id: my-flow\ntasks:\n  - id: t\n    type: io.kestra.plugin.unknown.Task\n");

        // Then
        assertThat(result.enabled()).isFalse();
        assertThat(result.missingTypes()).isEmpty();
        assertThat(result.artifacts()).isEmpty();
    }

    @Test
    void shouldDetectMissingTypesAndTheirCatalogArtifacts() {
        // Given
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);
        when(catalogService.get()).thenReturn(
            List.of(manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http"))
        );
        PluginAutoInstallService service = enabledService();

        // When
        PluginAutoInstallDetectResult result = service.detect("id: my-flow\ntasks:\n  - id: t\n    type: io.kestra.plugin.http.request.Request\n");

        // Then
        assertThat(result.enabled()).isTrue();
        assertThat(result.missingTypes()).containsExactly("io.kestra.plugin.http.request.Request");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().artifactId()).isEqualTo("plugin-http");
    }

    @Test
    void shouldAcceptOnlyCatalogArtifacts() {
        // Given
        when(catalogService.get()).thenReturn(
            List.of(manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http"))
        );
        PluginAutoInstallService service = enabledService();

        // When / Then — the allowlist matches on groupId + artifactId
        assertThat(service.isFromCatalog(PluginArtifact.fromCoordinates("io.kestra.plugin:plugin-http:LATEST"))).isTrue();
        assertThat(service.isFromCatalog(PluginArtifact.fromCoordinates("com.evil:backdoor:1.0.0"))).isFalse();
        assertThat(service.isFromCatalog(null)).isFalse();
    }

    // ─── installMissingPlugins ────────────────────────────────────────────────

    @Test
    void shouldInstallDeduplicatedArtifactsWhenTypesAreMissing() throws Exception {
        // Given — two missing types resolving to the same catalog artifact
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
              - id: t2
                type: io.kestra.plugin.http.download.Download
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);
        when(catalogService.get()).thenReturn(
            List.of(manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http"))
        );

        UUID jobId = UUID.randomUUID();
        when(installJobRegistry.submit(anyList())).thenReturn(jobId);
        when(installJobRegistry.awaitTerminal(jobId, SAVE_TIMEOUT))
            .thenAnswer(invocation -> Optional.of(succeededJob()));

        PluginAutoInstallService service = enabledService();

        // When
        service.installMissingPlugins(yaml);

        // Then — the shared artifact is submitted exactly once, waited on with the shorter save-path timeout
        ArgumentCaptor<List<PluginArtifact>> captor = ArgumentCaptor.captor();
        verify(installJobRegistry).submit(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().artifactId()).isEqualTo("plugin-http");
        verify(installJobRegistry).awaitTerminal(jobId, SAVE_TIMEOUT);
    }

    @Test
    void shouldNotInstallWhenDisabled() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        PluginAutoInstallService service = disabledService();

        // When
        service.installMissingPlugins(yaml);

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    @Test
    void shouldNotInstallWhenAllTypesAreRegistered() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.http.request.Request"))
            .thenReturn((Class) Object.class);

        PluginAutoInstallService service = enabledService();

        // When
        service.installMissingPlugins(yaml);

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    @Test
    void shouldNotInstallWhenNoCatalogArtifactMatches() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.unknown.Task
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);
        when(catalogService.get()).thenReturn(List.of());

        PluginAutoInstallService service = enabledService();

        // When
        service.installMissingPlugins(yaml);

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    @Test
    void shouldNotThrowWhenInstallFails() {
        // Given — the registry blows up: the save path must never see the exception
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);
        when(catalogService.get()).thenReturn(
            List.of(manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http"))
        );
        when(installJobRegistry.submit(anyList())).thenThrow(new RuntimeException("boom"));

        PluginAutoInstallService service = enabledService();

        // When / Then
        assertThatCode(() -> service.installMissingPlugins(yaml)).doesNotThrowAnyException();
    }

    // ─── computed default gating ──────────────────────────────────────────────

    @Test
    void shouldBeEnabledByDefaultForOssWithLocalStorage() {
        // Given / When — no explicit enabled property, OSS edition, local-filesystem storage
        PluginAutoInstallService service = new PluginAutoInstallService(
            catalogService, pluginRegistry, () -> installJobRegistry, new EditionProvider(),
            Optional.of("local"), Optional.empty(), Optional.empty(), Optional.empty()
        );

        // Then
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void shouldBeDisabledByDefaultForNonLocalStorage() {
        // Given / When — a standalone deployment on S3 must stay inert
        PluginAutoInstallService service = new PluginAutoInstallService(
            catalogService, pluginRegistry, () -> installJobRegistry, new EditionProvider(),
            Optional.of("s3"), Optional.empty(), Optional.empty(), Optional.empty()
        );

        // Then
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldHonorExplicitEnabledPropertyOnNonLocalStorage() {
        // Given / When — an explicit opt-in always wins over the computed default
        PluginAutoInstallService service = new PluginAutoInstallService(
            catalogService, pluginRegistry, () -> installJobRegistry, new EditionProvider(),
            Optional.of("s3"), Optional.of(true), Optional.empty(), Optional.empty()
        );

        // Then
        assertThat(service.isEnabled()).isTrue();
    }

    // ─── installMissingTypes ──────────────────────────────────────────────────

    @Test
    void shouldInstallDeduplicatedArtifactsForAggregatedTypes() throws Exception {
        // Given — two missing types (as aggregated by the first-sync migration) sharing one artifact
        when(catalogService.get()).thenReturn(
            List.of(manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http"))
        );
        UUID jobId = UUID.randomUUID();
        when(installJobRegistry.submit(anyList())).thenReturn(jobId);
        when(installJobRegistry.awaitTerminal(jobId, INSTALL_TIMEOUT))
            .thenAnswer(invocation -> Optional.of(succeededJob()));

        PluginAutoInstallService service = enabledService();

        // When
        service.installMissingTypes(
            Set.of(
                "io.kestra.plugin.http.request.Request",
                "io.kestra.plugin.http.download.Download"
            )
        );

        // Then
        ArgumentCaptor<List<PluginArtifact>> captor = ArgumentCaptor.captor();
        verify(installJobRegistry).submit(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().artifactId()).isEqualTo("plugin-http");
    }

    @Test
    void shouldNotInstallTypesWhenDisabled() {
        // Given
        PluginAutoInstallService service = disabledService();

        // When
        service.installMissingTypes(Set.of("io.kestra.plugin.http.request.Request"));

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    // ─── installMissingConfiguredPlugins ──────────────────────────────────────

    @Test
    void shouldInstallStorageArtifactWhenConfiguredStorageTypeIsMissing() throws Exception {
        // Given — no registered plugin provides the configured "s3" storage backend
        when(pluginRegistry.plugins()).thenReturn(List.of());
        UUID jobId = UUID.randomUUID();
        when(installJobRegistry.submit(anyList())).thenReturn(jobId);
        when(installJobRegistry.awaitTerminal(jobId, INSTALL_TIMEOUT))
            .thenAnswer(invocation -> Optional.of(succeededJob()));

        PluginAutoInstallService service = enabledService(Optional.of("s3"));

        // When
        service.installMissingConfiguredPlugins();

        // Then — the conventional storage coordinates are submitted
        ArgumentCaptor<List<PluginArtifact>> captor = ArgumentCaptor.captor();
        verify(installJobRegistry).submit(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().groupId()).isEqualTo("io.kestra.storage");
        assertThat(captor.getValue().getFirst().artifactId()).isEqualTo("storage-s3");
    }

    @Test
    void shouldNotInstallStorageArtifactWhenConfiguredStorageTypeIsRegistered() {
        // Given — a registered plugin already provides the "s3" storage backend
        RegisteredPlugin registeredPlugin = mock(RegisteredPlugin.class);
        doReturn(List.of(S3LikeStorage.class)).when(registeredPlugin).getStorages();
        when(pluginRegistry.plugins()).thenReturn(List.of(registeredPlugin));

        PluginAutoInstallService service = enabledService(Optional.of("S3"));

        // When
        service.installMissingConfiguredPlugins();

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    @Test
    void shouldNotInstallStorageArtifactWhenNoStorageTypeIsConfigured() {
        // Given
        PluginAutoInstallService service = enabledService(Optional.empty());

        // When
        service.installMissingConfiguredPlugins();

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    @Test
    void shouldNotInstallConfiguredPluginsWhenDisabled() {
        // Given
        PluginAutoInstallService service = disabledService();

        // When
        service.installMissingConfiguredPlugins();

        // Then
        verify(installJobRegistry, never()).submit(anyList());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PluginAutoInstallService enabledService() {
        return enabledService(Optional.empty());
    }

    private PluginAutoInstallService enabledService(Optional<String> storageType) {
        return new PluginAutoInstallService(catalogService, pluginRegistry, () -> installJobRegistry, true, INSTALL_TIMEOUT, SAVE_TIMEOUT, storageType);
    }

    private PluginAutoInstallService disabledService() {
        return new PluginAutoInstallService(catalogService, pluginRegistry, () -> installJobRegistry, false, INSTALL_TIMEOUT, SAVE_TIMEOUT, Optional.of("s3"));
    }

    private PluginInstallJob succeededJob() {
        return PluginInstallJob.pending(List.of()).running(Instant.now()).succeeded(Instant.now());
    }

    private PluginCatalogService.PluginManifest manifest(String groupId, String artifactId, String group) {
        return new PluginCatalogService.PluginManifest(artifactId, groupId, artifactId, group);
    }

    @Plugin.Id("s3")
    abstract static class S3LikeStorage implements StorageInterface {
    }
}
