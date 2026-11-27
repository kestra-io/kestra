package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpToolAnnotations;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class McpPlatformToolTest {
    @Inject
    List<AiPlatformTool> platformTools;

    @Inject
    List<AiAuthoringTool> authoringTools;

    @Test
    void shouldOptInOnlyTheToolsMeantForMcpClientsWhenCatalogIsBuilt() {
        // Given — the docs tools are stubbed so the catalog holds every tool bean without reaching kestra.io
        DocsMcpToolProvider docsMcpToolProvider = mock(DocsMcpToolProvider.class);
        when(docsMcpToolProvider.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(platformTools, authoringTools, docsMcpToolProvider, (permission, tenant, principal) -> true, new DefaultAgentToolCallScope(), new ToolResultBudget(100_000, null));

        // When
        List<ToolCatalog.ToolEntry> exposed = catalog.entries().stream()
            .filter(entry -> entry.tool() instanceof McpExposedTool)
            .toList();

        // Then
        assertThat(exposed).extracting(ToolCatalog.ToolEntry::name).containsExactlyInAnyOrder(
            "read_flow", "list_flows", "read_execution", "list_executions", "read_execution_logs",
            "search_plugins", "get_plugin_schema", "list_installed_plugins", "read_plugin_documentation", "validate_flow", "restart_execution",
            "search_flow_sources", "list_flow_revisions", "list_flow_dependencies", "list_flow_triggers",
            "create_flow", "update_flow", "enable_flow", "disable_flow", "delete_flow", "read_instance_info",
            "execute_flow", "kill_execution", "pause_execution", "change_execution_state", "update_execution_labels",
            "delete_execution", "read_execution_outputs",
            "replay_execution", "resume_execution", "force_run_execution", "change_task_run_state", "backfill_flow",
            "list_namespaces", "list_namespace_dependencies",
            "list_namespace_files", "read_namespace_file", "write_namespace_file", "create_namespace_directory", "move_namespace_file", "delete_namespace_file",
            "list_kv_entries", "read_kv_entry", "create_kv_entry", "update_kv_entry", "delete_kv_entry",
            "search_logs", "delete_execution_logs", "delete_flow_logs",
            "author_flow", "revise_flow"
        );
        assertThat(exposed).allSatisfy(entry ->
        {
            McpToolAnnotations annotations = ((McpExposedTool) entry.tool()).annotations();
            assertThat(annotations).as("annotations of %s", entry.name()).isNotNull();
            assertThat(annotations.readOnly()).as("readOnly of %s", entry.name()).isEqualTo(entry.isAuthoring() || AgentToolFamily.READ == entry.family());
            if (annotations.destructive() && !annotations.readOnly()) {
                assertThat(entry.writePolicy()).as("writePolicy of destructive %s", entry.name()).isEqualTo(AgentWritePolicy.CONFIRM);
            }
        });
    }
}
