package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
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
    void shouldOptInOnlyThePlatformToolsMeantForMcpClientsWhenCatalogIsBuilt() {
        // Given — the docs tools are stubbed so the catalog holds every tool bean without reaching kestra.io
        DocsMcpToolProvider docsMcpToolProvider = mock(DocsMcpToolProvider.class);
        when(docsMcpToolProvider.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(platformTools, authoringTools, docsMcpToolProvider, (permission, tenant, principal) -> true, new DefaultAgentToolCallScope());

        // When
        List<ToolCatalog.ToolEntry> exposed = catalog.entries().stream()
            .filter(entry -> entry.tool() instanceof McpPlatformTool)
            .toList();

        // Then
        assertThat(exposed).extracting(ToolCatalog.ToolEntry::name).containsExactlyInAnyOrder(
            "read_flow", "list_flows", "read_execution", "list_executions", "read_execution_logs",
            "search_plugins", "get_plugin_schema", "validate_flow", "restart_execution",
            "search_flow_sources", "list_flow_revisions", "list_flow_dependencies", "list_flow_triggers",
            "create_flow", "update_flow", "enable_flow", "disable_flow", "delete_flow",
            "execute_flow", "kill_execution", "pause_execution", "change_execution_state", "update_execution_labels",
            "delete_execution", "read_execution_outputs",
            "replay_execution", "resume_execution", "force_run_execution", "change_task_run_state", "backfill_flow",
            "list_kv_entries", "read_kv_entry", "create_kv_entry", "update_kv_entry", "delete_kv_entry"
        );
        assertThat(exposed).allSatisfy(entry ->
        {
            McpToolAnnotations annotations = ((McpPlatformTool) entry.tool()).annotations();
            assertThat(annotations).as("annotations of %s", entry.name()).isNotNull();
            assertThat(annotations.readOnly()).as("readOnly of %s", entry.name()).isEqualTo(AgentToolFamily.READ == entry.family());
        });
    }
}
