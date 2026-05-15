package io.kestra.core.plugins;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginAutoInstallServiceTest {

    private PluginCatalogService catalogService;
    private PluginManager pluginManager;
    private PluginRegistry pluginRegistry;
    private JsonSchemaCache jsonSchemaCache;

    @BeforeEach
    void setUp() {
        catalogService = mock(PluginCatalogService.class);
        pluginManager = mock(PluginManager.class);
        pluginRegistry = mock(PluginRegistry.class);
        jsonSchemaCache = mock(JsonSchemaCache.class);
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
        when(catalogService.get()).thenReturn(List.of(
            manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http")
        ));
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
        when(catalogService.get()).thenReturn(List.of(
            manifest("io.kestra.plugin", "plugin-scripts", "io.kestra.plugin.scripts"),
            manifest("io.kestra.plugin", "plugin-script-python", "io.kestra.plugin.scripts.python")
        ));
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

    // ─── installMissingPlugins ────────────────────────────────────────────────

    @Test
    void shouldInstallMissingPluginAndClearSchemaCache() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        when(pluginRegistry.findClassByIdentifier("io.kestra.plugin.http.request.Request")).thenReturn(null);
        when(catalogService.get()).thenReturn(List.of(
            manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http")
        ));
        PluginArtifact expectedArtifact = PluginArtifact.fromCoordinates("io.kestra.plugin:plugin-http:LATEST");
        when(pluginManager.install(anyList(), anyList(), eq(true), any())).thenReturn(List.of(expectedArtifact));

        PluginAutoInstallService service = enabledService();

        // When
        List<PluginArtifact> installed = service.installMissingPlugins(yaml);

        // Then
        assertThat(installed).hasSize(1);
        assertThat(installed.getFirst().artifactId()).isEqualTo("plugin-http");
        verify(jsonSchemaCache).clear();
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

        // When
        List<PluginArtifact> installed = service.installMissingPlugins(yaml);

        // Then
        assertThat(installed).isEmpty();
        verify(pluginManager, never()).install(anyList(), anyList(), any(Boolean.class), any());
        verify(jsonSchemaCache, never()).clear();
    }

    @Test
    void shouldBeNoOpWhenAllTypesAlreadyInstalled() {
        // Given
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn((Class) Object.class);

        PluginAutoInstallService service = enabledService();

        // When
        List<PluginArtifact> installed = service.installMissingPlugins(yaml);

        // Then
        assertThat(installed).isEmpty();
        verify(pluginManager, never()).install(anyList(), anyList(), any(Boolean.class), any());
    }

    @Test
    void shouldNotInstallDeduplicatedArtifacts() {
        // Given — two types from the same plugin
        String yaml = """
            id: my-flow
            namespace: company
            tasks:
              - id: t1
                type: io.kestra.plugin.http.request.Request
              - id: t2
                type: io.kestra.plugin.http.request.Options
            """;
        when(pluginRegistry.findClassByIdentifier(anyString())).thenReturn(null);
        when(catalogService.get()).thenReturn(List.of(
            manifest("io.kestra.plugin", "plugin-http", "io.kestra.plugin.http")
        ));
        PluginArtifact artifact = PluginArtifact.fromCoordinates("io.kestra.plugin:plugin-http:LATEST");
        when(pluginManager.install(anyList(), anyList(), eq(true), any())).thenReturn(List.of(artifact));

        PluginAutoInstallService service = enabledService();

        // When
        service.installMissingPlugins(yaml);

        // Then — PluginManager.install is called with a single de-duplicated artifact
        verify(pluginManager).install(eq(List.of(artifact)), anyList(), eq(true), any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PluginAutoInstallService enabledService() {
        return new PluginAutoInstallService(catalogService, pluginManager, pluginRegistry, jsonSchemaCache, true);
    }

    private PluginAutoInstallService disabledService() {
        return new PluginAutoInstallService(catalogService, pluginManager, pluginRegistry, jsonSchemaCache, false);
    }

    private PluginCatalogService.PluginManifest manifest(String groupId, String artifactId, String group) {
        return new PluginCatalogService.PluginManifest(artifactId, null, groupId, artifactId, group);
    }
}
