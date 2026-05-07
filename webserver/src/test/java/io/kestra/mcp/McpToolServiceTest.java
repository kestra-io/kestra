package io.kestra.mcp;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.kestra.plugin.core.trigger.Schedule;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "h2")
class McpToolServiceTest {
    String SERVER_ID;

    private static final McpToolTrigger MCP_TRIGGER_ONE = McpToolTrigger.builder()
        .id("mcp-trigger-one")
        .type(McpToolTrigger.class.getName())
        .toolName("tool-one")
        .title("Tool One")
        .toolDescription("First test tool")
        .annotations(new McpToolTrigger.Annotations(true, false, false, true, false))
        .build();

    private static final McpToolTrigger MCP_TRIGGER_TWO = McpToolTrigger.builder()
        .id("mcp-trigger-two")
        .type(McpToolTrigger.class.getName())
        .toolName("tool-two")
        .title("Tool Two")
        .toolDescription("Second test tool")
        .annotations(new McpToolTrigger.Annotations(false, true, false, false, false))
        .build();

    private static final McpToolTrigger DISABLED_MCP_TRIGGER = McpToolTrigger.builder()
        .id("disabled-mcp-trigger")
        .type(McpToolTrigger.class.getName())
        .toolName("disabled-tool")
        .title("Disabled Tool")
        .toolDescription("A disabled test tool")
        .annotations(new McpToolTrigger.Annotations(true, false, false, true, false))
        .disabled(true)
        .build();

    @Inject
    McpToolService mcpToolService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @BeforeEach
    void beforeEach() {
        SERVER_ID = UUID.randomUUID().toString();
    }

    @Test
    void givenFlowWithMcpToolTrigger_whenListToolSpecsForServer_thenReturnToolSpecForFlow() {
        // Given
        FlowWithSource savedFlow = flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().tool().name()).isEqualTo(MCP_TRIGGER_ONE.getToolName());
    }

    @Test
    void givenFlowWithMcpToolTrigger_whenListToolSpecsForServer_thenAnnotationsAreMapped() {
        // Given — MCP_TRIGGER_ONE has Annotations(readOnly=true, openWorld=false, destructive=false, idempotent=true)
        flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        ToolAnnotations annotations = tools.getFirst().tool().annotations();
        assertThat(annotations).isNotNull();
        assertThat(annotations.readOnlyHint()).isTrue();
        assertThat(annotations.openWorldHint()).isFalse();
        assertThat(annotations.destructiveHint()).isFalse();
        assertThat(annotations.idempotentHint()).isTrue();
    }

    @Test
    void givenFlowWithoutMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given
        Schedule scheduleTrigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 0 * * *")
            .build();
        FlowWithSource savedFlow = flowRepository.create(GenericFlow.of(buildFlow(List.of(scheduleTrigger))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).isEmpty();
    }

    @Test
    void givenFlowWithMultipleMcpToolTrigger_whenListToolSpecsForServer_thenToolSpecsReturnedForEachTrigger() {
        // Given
        flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE, MCP_TRIGGER_TWO))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).hasSize(2);
        assertThat(tools.stream().map(McpServerFeatures.AsyncToolSpecification::tool).map(McpSchema.Tool::name).toList())
            .containsExactlyInAnyOrder(MCP_TRIGGER_ONE.getToolName(), MCP_TRIGGER_TWO.getToolName());

    }

    @Test
    void givenFlowWithToolTrigger_whenListToolSpecsForServerForSameTriggerMultipleTimes_thenCachedToolSpecsReturned() {
        // Given
        flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> firstCall = mcpToolService.listToolSpecsForServer(null, SERVER_ID);
        List<McpServerFeatures.AsyncToolSpecification> secondCall = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(1);
        assertThat(firstCall.getFirst().callHandler()).isSameAs(secondCall.getFirst().callHandler());
    }

    @Test
    void givenMultipleFlowsWithMcpToolTrigger_whenListToolSpecsForServer_thenToolSpecsReturnedForEachFlow() {
        // Given
        FlowWithSource savedFlow1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE))));
        FlowWithSource savedFlow2 = flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_TWO))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).hasSize(2);
        assertThat(tools.stream().map(McpServerFeatures.AsyncToolSpecification::tool).map(McpSchema.Tool::name).toList())
            .containsExactlyInAnyOrder(MCP_TRIGGER_ONE.getToolName(), MCP_TRIGGER_TWO.getToolName());
    }

    @Test
    void givenDeletedFlowWithMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given
        FlowWithSource savedFlow = flowRepository.create(GenericFlow.of(buildFlow(List.of(MCP_TRIGGER_ONE))));
        flowRepository.delete(savedFlow);

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).isEmpty();
    }

    @Test
    void givenFlowWithDisabledMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given
        FlowWithSource savedFlow = flowRepository.create(GenericFlow.of(buildFlow(List.of(DISABLED_MCP_TRIGGER))));

        // When
        List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

        // Then
        assertThat(tools).isEmpty();
    }

    @Test
    void givenFlowsWithoutTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given
        FlowWithSource savedFlow = flowRepository.create(GenericFlow.of(buildFlow(List.of())));
        try {
            // When
            List<McpServerFeatures.AsyncToolSpecification> tools = mcpToolService.listToolSpecsForServer(null, SERVER_ID);

            // Then
            assertThat(tools).isEmpty();
        } finally {
            flowRepository.deleteWithoutAcl(savedFlow);
        }
    }

    @SuppressWarnings("unchecked")
    private List<AbstractTrigger> addServerIdToMcpTrigger(List<AbstractTrigger> triggers) {
        return triggers.stream().map(trigger -> {
                if (trigger instanceof McpToolTrigger toolTrigger) {
                    return toolTrigger.toBuilder().mcpServer(SERVER_ID).build();
                }
                return trigger;
            }
        ).toList();
    }

    private Flow buildFlow(List<AbstractTrigger> triggers) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("namespace")
            .tasks(List.of(
                Return.builder()
                    .id("task")
                    .type(Return.class.getName())
                    .format(Property.ofValue("test"))
                    .build()
            ))
            .triggers(addServerIdToMcpTrigger(triggers))
            .build();
    }
}
