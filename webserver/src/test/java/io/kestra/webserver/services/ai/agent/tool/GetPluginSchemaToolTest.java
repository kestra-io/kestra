package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetPluginSchemaToolTest {
    private static final String PLUGIN_TYPE = "io.kestra.plugin.core.log.Log";

    private PluginRegistry pluginRegistry;
    private JsonSchemaGenerator jsonSchemaGenerator;
    private GetPluginSchemaTool tool;

    @BeforeEach
    void setUp() {
        pluginRegistry = mock(PluginRegistry.class);
        jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        tool = new GetPluginSchemaTool(pluginRegistry, jsonSchemaGenerator);
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void shouldReturnSchemaWhenPluginTypeExists() {
        // Given — the registry resolves the type and the generator produces a schema for its class
        PluginClassAndMetadata<? extends Plugin> metadata = new PluginClassAndMetadata(FakePlugin.class, Plugin.class, "core", null, "Log", null, null);
        Map<String, Object> schema = Map.of("properties", Map.of("message", Map.of("type", "string")));
        when(pluginRegistry.findMetadataByIdentifier(PLUGIN_TYPE)).thenReturn(Optional.of(metadata));
        when(jsonSchemaGenerator.schemas(FakePlugin.class)).thenReturn(schema);

        // When
        GetPluginSchemaTool.Result result = tool.getPluginSchema(PLUGIN_TYPE);

        // Then — the generated schema, wrapped with its plugin type
        assertThat(result.pluginType()).isEqualTo(PLUGIN_TYPE);
        assertThat(result.schema()).isEqualTo(schema);
    }

    @Test
    void shouldThrowWhenPluginTypeNotFound() {
        // Given
        when(pluginRegistry.findMetadataByIdentifier("io.unknown.Type")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> tool.getPluginSchema("io.unknown.Type"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin type not found: 'io.unknown.Type'");
        verifyNoInteractions(jsonSchemaGenerator);
    }

    private interface FakePlugin extends Plugin {
    }
}
