package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code get_plugin_schema} resolves a real plugin type through the plugin
 * registry and returns its generated JSON schema, and rejects an unknown type.
 */
@KestraTest(environments = "memory")
class GetPluginSchemaToolTest {
    private static final String PLUGIN_TYPE = "io.kestra.plugin.core.log.Log";

    @Inject
    private GetPluginSchemaTool tool;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnSchemaWhenPluginTypeExists() {
        // When
        GetPluginSchemaTool.Result result = tool.getPluginSchema(PLUGIN_TYPE);

        // Then — the declared properties are returned without the documentation-only keys
        assertThat(result.pluginType()).isEqualTo(PLUGIN_TYPE);
        assertThat(result.properties()).isNotEmpty()
            .doesNotContainKeys("$schema", "$examples", "$metrics", "$deprecated", "$beta", "title", "description");
    }

    @Test
    void shouldThrowWhenPluginTypeNotFound() {
        assertThatThrownBy(() -> tool.getPluginSchema("io.unknown.Type"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin type not found: 'io.unknown.Type'");
    }
}
