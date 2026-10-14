package io.kestra.mcp;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "h2")
@io.micronaut.context.annotation.Property(name = "kestra.server-type", value = "WEBSERVER")
class McpServerHandlerTransportTest {

    @Inject
    McpServerHandlerTransport mcpServerHandlerTransport;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void shouldShutDownEveryBuiltServerWhenContextIsDestroyed() {
        // Given — two servers built by the registry, each holding a keep-alive scheduler
        String serverA = UUID.randomUUID().toString();
        String serverB = UUID.randomUUID().toString();
        flowRepository.create(GenericFlow.of(buildFlowWithMcpTrigger(serverA)));
        flowRepository.create(GenericFlow.of(buildFlowWithMcpTrigger(serverB)));

        mcpServerHandlerTransport.getServerHandler(contextFor(serverA));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverB));
        assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverA).collectList().block()).hasSize(1);
        assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverB).collectList().block()).hasSize(1);

        // When — the context closes
        mcpServerHandlerTransport.close();

        // Then — no server is left behind, so no keep-alive scheduler outlives the context
        assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverA).collectList().block()).isEmpty();
        assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverB).collectList().block()).isEmpty();
    }

    @Test
    void shouldCompleteWhenClosingWithoutAnyBuiltServer() {
        // Given / When / Then — closing an untouched registry must not block or throw
        mcpServerHandlerTransport.close();
    }

    private KestraMcpTransportContext contextFor(String serverId) {
        return KestraMcpTransportContext.builder()
            .tenantId(null)
            .serverId(serverId)
            .build();
    }

    private Flow buildFlowWithMcpTrigger(String serverId) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.mcp.shutdown")
            .tasks(
                List.of(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("done"))
                        .build()
                )
            )
            .triggers(
                List.of(
                    McpToolTrigger.builder()
                        .id("mcp-trigger")
                        .type(McpToolTrigger.class.getName())
                        .toolName("tool-" + IdUtils.create().toLowerCase())
                        .title("Test Tool")
                        .toolDescription("A test MCP tool")
                        .mcpServer(serverId)
                        .build()
                )
            )
            .build();
    }
}
