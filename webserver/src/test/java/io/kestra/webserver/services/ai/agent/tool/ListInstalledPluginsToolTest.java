package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.kestra.core.Helpers;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration check that {@code list_installed_plugins} lists the real plugin registry, applies the per-field
 * filters the way the catalog sends them, and pages its results.
 */
@KestraTest(environments = "memory")
class ListInstalledPluginsToolTest {
    private static final String CORE = "core";
    private static final String TEMPLATE = "plugin-template-test";

    @Inject
    private ListInstalledPluginsTool tool;

    @BeforeAll
    static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldListInstalledPluginsWhenInvoked() {
        ListInstalledPluginsTool.Result result = tool.listInstalledPlugins(List.of(), null);

        assertThat(result.plugins())
            .anyMatch(plugin -> CORE.equals(plugin.artifactId()) && plugin.types().getOrDefault(PluginKind.TASK, 0) > 0);
        assertThat(result.plugins())
            .anyMatch(plugin -> TEMPLATE.equals(plugin.artifactId())
                && "0.24.0-SNAPSHOT".equals(plugin.version())
                && "io.kestra.plugin.templates".equals(plugin.group()));
        assertThat(result.total()).isEqualTo(result.plugins().size());
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void shouldApplyQueryFilterWhenCalledTheWayTheCatalogDoes() throws Exception {
        Map<String, Object> result = callThroughExecutor("""
            { "QUERY": { "operator": "EQUALS", "value": "template" } }""");

        assertThat(artifactIds(result)).isNotEmpty().containsOnly(TEMPLATE);
    }

    @Test
    void shouldSplitCommaSeparatedValuesWhenArtifactIdFilterUsesIn() throws Exception {
        Map<String, Object> in = callThroughExecutor("""
            { "ARTIFACT_ID": { "operator": "IN", "value": "core, plugin-template-test" } }""");
        Map<String, Object> notIn = callThroughExecutor("""
            { "ARTIFACT_ID": { "operator": "NOT_IN", "value": "core" } }""");

        assertThat(artifactIds(in)).contains(CORE, TEMPLATE).containsOnly(CORE, TEMPLATE);
        assertThat(artifactIds(notIn)).contains(TEMPLATE).doesNotContain(CORE);
    }

    @Test
    void shouldReturnEmptyPageWhenPageIsPastTheEnd() {
        ListInstalledPluginsTool.Result first = tool.listInstalledPlugins(List.of(), null);
        ListInstalledPluginsTool.Result beyond = tool.listInstalledPlugins(List.of(), 99);

        assertThat(beyond.plugins()).isEmpty();
        assertThat(beyond.total()).isEqualTo(first.total());
        assertThat(beyond.page()).isEqualTo(99);
    }

    @Test
    void shouldRejectPageWhenBelowOne() {
        assertThatThrownBy(() -> tool.listInstalledPlugins(List.of(), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page must be 1 or greater");
    }

    private Map<String, Object> callThroughExecutor(final String arguments) throws Exception {
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(
            tool,
            ListInstalledPluginsTool.class.getMethod("listInstalledPlugins", List.class, Integer.class)
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("list_installed_plugins")
            .arguments(arguments)
            .build();
        String json = executor.executeWithContext(request, AgentCallContext.into(AgentCallContext.Context.ofTenant(MAIN_TENANT))).resultText();
        return JacksonMapper.toMap(json);
    }

    @SuppressWarnings("unchecked")
    private static List<String> artifactIds(final Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("plugins")).stream()
            .map(plugin -> (String) plugin.get("artifactId"))
            .toList();
    }
}
