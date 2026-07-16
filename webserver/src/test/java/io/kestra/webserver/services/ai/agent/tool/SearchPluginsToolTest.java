package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import io.swagger.v3.oas.annotations.media.Schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchPluginsToolTest {

    @Schema(title = "Log a message to the console")
    private static final class LogTask {
    }

    @Schema(title = "Query a Postgres database")
    private static final class PostgresQueryTask {
    }

    @Schema(title = "Old log task", deprecated = true)
    private static final class DeprecatedLogTask {
    }

    private PluginRegistry pluginRegistry;
    private SearchPluginsTool tool;

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() {
        pluginRegistry = mock(PluginRegistry.class);
        RegisteredPlugin registeredPlugin = mock(RegisteredPlugin.class);
        when(registeredPlugin.allClassGrouped()).thenReturn(
            (Map) Map.of(
                "tasks", List.of(LogTask.class, PostgresQueryTask.class, DeprecatedLogTask.class)
            )
        );
        when(pluginRegistry.plugins()).thenReturn(List.of(registeredPlugin));
        tool = new SearchPluginsTool(pluginRegistry);
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldMatchOnClassNameAndTitleCaseInsensitivelyWhenPluginsExist() {
        // When — "log" matches LogTask by name/title but must skip the deprecated one
        SearchPluginsTool.Result result = tool.searchPlugins("LOG");

        // Then
        assertThat(result.plugins()).containsExactly(
            new SearchPluginsTool.PluginMatch(LogTask.class.getName(), "Log a message to the console")
        );
    }

    @Test
    void shouldMatchOnTitleWhenKeywordOnlyAppearsInTitle() {
        // When — "database" only appears in the @Schema title
        SearchPluginsTool.Result result = tool.searchPlugins("database");

        // Then
        assertThat(result.plugins()).containsExactly(
            new SearchPluginsTool.PluginMatch(PostgresQueryTask.class.getName(), "Query a Postgres database")
        );
    }

    @Test
    void shouldReturnEmptyListWhenNothingMatches() {
        // When
        SearchPluginsTool.Result result = tool.searchPlugins("does-not-exist");

        // Then
        assertThat(result.plugins()).isEmpty();
    }
}
