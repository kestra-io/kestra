package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration check that {@code search-plugins} runs against the real plugin registry: a keyword
 * matches an installed plugin type, and a keyword that matches nothing returns an empty list.
 */
@KestraTest(environments = "memory")
class SearchPluginsToolTest {
    @Inject
    private SearchPluginsTool tool;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldMatchInstalledPluginByType() {
        // When — a keyword that appears in a core plugin's fully-qualified type
        SearchPluginsTool.Result result = tool.searchPlugins("core.debug.Return");

        // Then — the core Return task is found
        assertThat(result.plugins())
            .anyMatch(match -> "io.kestra.plugin.core.debug.Return".equals(match.type()));
    }

    @Test
    void shouldReturnEmptyListWhenNothingMatches() {
        assertThat(tool.searchPlugins("definitely-not-an-installed-plugin-xyz").plugins()).isEmpty();
    }
}
