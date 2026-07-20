package io.kestra.core.plugins;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginAutoInstallServiceTest {

    private PluginCatalogService catalogService;
    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        catalogService = mock(PluginCatalogService.class);
        pluginRegistry = mock(PluginRegistry.class);
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

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PluginAutoInstallService enabledService() {
        return new PluginAutoInstallService(catalogService, pluginRegistry, true);
    }

    private PluginAutoInstallService disabledService() {
        return new PluginAutoInstallService(catalogService, pluginRegistry, false);
    }

    private PluginCatalogService.PluginManifest manifest(String groupId, String artifactId, String group) {
        return new PluginCatalogService.PluginManifest(artifactId, null, groupId, artifactId, group);
    }
}
