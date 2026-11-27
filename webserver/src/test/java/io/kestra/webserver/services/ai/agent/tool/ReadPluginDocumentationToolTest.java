package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code read_plugin_documentation} renders the real documentation of an
 * installed plugin type, without front matter, and cuts it to the requested length.
 */
@KestraTest(environments = "memory")
class ReadPluginDocumentationToolTest {
    private static final String PLUGIN_TYPE = "io.kestra.plugin.core.log.Log";

    @Inject
    private ReadPluginDocumentationTool tool;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldRenderDocumentationWhenPluginTypeExists() {
        ReadPluginDocumentationTool.Result result = tool.readPluginDocumentation(PLUGIN_TYPE);

        assertThat(result.pluginType()).isEqualTo(PLUGIN_TYPE);
        assertThat(result.markdown())
            .doesNotStartWith("---")
            .contains("type: \"%s\"".formatted(PLUGIN_TYPE))
            .contains("## Properties");
    }

    @Test
    void shouldThrowWhenPluginTypeNotFound() {
        assertThatThrownBy(() -> tool.readPluginDocumentation("io.unknown.Type"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Plugin type not found: 'io.unknown.Type'");
    }
}
