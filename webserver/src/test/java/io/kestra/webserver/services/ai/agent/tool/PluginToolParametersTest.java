package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plugin versions are an Enterprise feature: the open-source plugin tools must not offer a {@code version} parameter.
 */
@KestraTest(environments = "memory")
class PluginToolParametersTest {
    @Inject
    private ToolCatalog catalog;

    @Test
    void shouldNotOfferVersionParameterWhenPluginToolsAreOpenSource() {
        for (String tool : new String[] { "get_plugin_schema", "read_plugin_documentation", "search_plugins", "list_installed_plugins" }) {
            assertThat(parameters(tool)).as("parameters of %s", tool).doesNotContain("version");
        }
        assertThat(parameters("get_plugin_schema")).containsExactly("pluginType");
        assertThat(parameters("read_plugin_documentation")).containsExactly("pluginType");
    }

    private java.util.Set<String> parameters(final String tool) {
        return catalog.entries().stream()
            .filter(entry -> entry.name().equals(tool))
            .findFirst()
            .orElseThrow()
            .specification().parameters().properties().keySet();
    }
}
