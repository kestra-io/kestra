package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code get-plugin-schema} resolves a real plugin type through the plugin
 * registry and returns its generated JSON schema, and rejects an unknown type.
 */
@KestraTest(environments = "memory")
class GetPluginSchemaToolTest {
    private static final String PLUGIN_TYPE = "io.kestra.plugin.core.log.Log";

    @Inject
    private GetPluginSchemaTool tool;

    @Inject
    private PluginRegistry pluginRegistry;

    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnSchemaWhenPluginTypeExists() {
        // When
        GetPluginSchemaTool.Result result = tool.getPluginSchema(PLUGIN_TYPE);

        // Then — the plugin's own properties are returned, wrapped with its plugin type
        assertThat(result.pluginType()).isEqualTo(PLUGIN_TYPE);
        assertThat(result.title()).isNotBlank();
        assertThat(result.description()).isNotBlank();
        assertThat(result.examples()).isNotEmpty();
        assertThat(result.examples()).allSatisfy(example -> assertThat(example.code()).isNotBlank());

        assertThat((java.util.Map<String, Object>) result.properties().get("properties"))
            .containsOnlyKeys("message", "level");
        assertThat((java.util.List<String>) result.properties().get("required")).containsExactly("message");
    }

    @Test
    void shouldOmitDocumentationOnlyKeysFromProperties() {
        // When
        GetPluginSchemaTool.Result result = tool.getPluginSchema(PLUGIN_TYPE);

        // Then — the doc-renderer keys are exposed as dedicated fields, not duplicated in the schema
        assertThat(result.properties())
            .doesNotContainKeys("$schema", "$examples", "$metrics", "$deprecated", "$beta", "title", "description");
    }

    @Test
    void shouldThrowWhenPluginTypeNotFound() {
        assertThatThrownBy(() -> tool.getPluginSchema("io.unknown.Type"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin type not found: 'io.unknown.Type'");
    }
}
