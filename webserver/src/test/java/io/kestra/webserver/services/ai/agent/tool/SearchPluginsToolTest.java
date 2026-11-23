package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration check that {@code search_plugins} runs against the real plugin registry: keyword and kind
 * narrow the installed types, deprecated types stay hidden unless asked for, and results come in pages.
 */
@KestraTest(environments = "memory")
class SearchPluginsToolTest {
    private static final String RETURN_TYPE = "io.kestra.plugin.core.debug.Return";
    private static final String DEPRECATED_TYPE = "io.kestra.core.plugins.test.DeprecatedTask";
    private static final int PAGE_SIZE = 50;

    @Inject
    private SearchPluginsTool tool;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldMatchInstalledPluginByType() {
        SearchPluginsTool.Result result = tool.searchPlugins("core.debug.Return", null, null, null);

        assertThat(result.plugins())
            .anyMatch(match -> RETURN_TYPE.equals(match.type()) && PluginKind.TASK == match.kind() && !match.pluginTitle().isBlank());
        assertThat(result.total()).isEqualTo(result.plugins().size());
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void shouldReturnOnlyTypesOfTheRequestedKindWhenKindIsGiven() {
        SearchPluginsTool.Result result = tool.searchPlugins(null, PluginKind.TRIGGER, null, null);

        assertThat(result.plugins()).isNotEmpty().allMatch(match -> PluginKind.TRIGGER == match.kind());
        assertThat(result.plugins()).anyMatch(match -> "io.kestra.plugin.core.trigger.Schedule".equals(match.type()));
        assertThat(result.plugins()).noneMatch(match -> RETURN_TYPE.equals(match.type()));
    }

    @Test
    void shouldLeaveDeprecatedTypesOutWhenNotRequested() {
        SearchPluginsTool.Result hidden = tool.searchPlugins("plugins.test.DeprecatedTask", null, null, null);
        SearchPluginsTool.Result shown = tool.searchPlugins("plugins.test.DeprecatedTask", null, true, null);

        assertThat(hidden.plugins()).isEmpty();
        assertThat(shown.plugins()).anyMatch(match -> DEPRECATED_TYPE.equals(match.type()) && match.deprecated());
    }

    @Test
    void shouldPageResultsWhenMoreTypesMatchThanOnePage() {
        SearchPluginsTool.Result first = tool.searchPlugins(null, null, null, null);
        SearchPluginsTool.Result second = tool.searchPlugins(null, null, null, 2);
        int pastTheEnd = (int) (first.total() / PAGE_SIZE) + 2;
        SearchPluginsTool.Result beyond = tool.searchPlugins(null, null, null, pastTheEnd);

        assertThat(first.total()).isGreaterThan(PAGE_SIZE);
        assertThat(first.plugins()).hasSize(PAGE_SIZE);
        assertThat(first.page()).isEqualTo(1);
        List<String> firstTypes = first.plugins().stream().map(SearchPluginsTool.PluginMatch::type).toList();
        assertThat(second.plugins()).isNotEmpty().noneMatch(match -> firstTypes.contains(match.type()));
        assertThat(second.total()).isEqualTo(first.total());
        assertThat(second.page()).isEqualTo(2);
        assertThat(beyond.plugins()).isEmpty();
        assertThat(beyond.total()).isEqualTo(first.total());
    }

    @Test
    void shouldRejectPageWhenBelowOne() {
        assertThatThrownBy(() -> tool.searchPlugins(null, null, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page must be 1 or greater");
    }

    @Test
    void shouldReturnEmptyListWhenNothingMatches() {
        SearchPluginsTool.Result result = tool.searchPlugins("definitely-not-an-installed-plugin-xyz", null, null, null);

        assertThat(result.plugins()).isEmpty();
        assertThat(result.total()).isZero();
    }
}
