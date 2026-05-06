package io.kestra.mcp;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.GenericTrigger;
import io.kestra.core.mcp.models.McpServerClusterEventPayload;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "h2")
@io.micronaut.context.annotation.Property(name = "kestra.server-type", value = "WEBSERVER")
class McpServerChangeNotifierTest {

    @Inject
    McpServerChangeNotifier notifier;

    @Inject
    McpServerHandlerTransport mcpServerHandlerTransport;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    BroadcastQueueInterface<ClusterEvent> clusterEventQueue;

    private String serverId;

    @BeforeEach
    void setUp() {
        serverId = UUID.randomUUID().toString();
    }

    @Test
    void givenFlowWithMcpTrigger_whenTriggerRemovedFromFlow_thenStaleToolIsRemovedFromServer() throws DeserializationException {
        // Given — v1 has a trigger on serverId; init the server so it holds v1's tool
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));
        awaitToolCount(serverId, 1);

        // When — v2 removes the trigger; server should detect removal via revision lookup
        FlowWithSource v2 = flowRepository.update(
            GenericFlow.of(buildFlowWithSameId(v1, List.of())),
            v1
        );
        notifier.handleFlowChange(asQueueEvent(v2, List.of()));

        // Then — stale tool is removed
        awaitToolCount(serverId, 0);
    }

    @Test
    void givenFlowWithMcpTrigger_whenMcpServerIdChangedInTrigger_thenBothServersAreRefreshed() throws DeserializationException {
        String serverA = serverId;
        String serverB = UUID.randomUUID().toString();

        // Given — v1 has a trigger on server-a; init both server handlers
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverA)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverA));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverB));
        awaitToolCount(serverA, 1);
        awaitToolCount(serverB, 0);

        // When — v2 moves the trigger to server-b
        FlowWithSource v2 = flowRepository.update(
            GenericFlow.of(buildFlowWithSameId(v1, List.of(buildMcpTrigger("t1", serverB)))),
            v1
        );
        notifier.handleFlowChange(asQueueEvent(v2, List.of(buildGenericMcpTrigger("t1", serverB))));

        // Then — server-a loses its tool, server-b gains one
        awaitToolCount(serverA, 0);
        awaitToolCount(serverB, 1);
    }

    @Test
    void givenNewFlow_whenFirstRevisionArrives_thenToolIsAddedToServer() throws DeserializationException {
        // Given — init the server handler before creating the flow
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));

        // When — revision 1 of a brand-new flow arrives
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        notifier.handleFlowChange(asQueueEvent(v1, List.of(buildGenericMcpTrigger("t1", serverId))));

        // Then — the new tool is registered on the server
        awaitToolCount(serverId, 1);
    }

    @Test
    void givenFlowWithMcpTrigger_whenFlowIsDisabled_thenToolIsRemovedFromServer() throws DeserializationException {
        // Given — v1 has a trigger on serverId; init the server
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));
        awaitToolCount(serverId, 1);

        // When — v2 is the same flow but disabled
        FlowWithSource v2 = flowRepository.update(
            GenericFlow.of(buildDisabledFlowWithSameId(v1, List.of(buildMcpTrigger("t1", serverId)))),
            v1
        );
        notifier.handleFlowChange(asQueueEvent(v2, List.of(buildGenericMcpTrigger("t1", serverId))));

        // Then — disabled flow is excluded from the tool list
        awaitToolCount(serverId, 0);
    }

    @Test
    void givenActiveServer_whenServerIsDeleted_thenServerIsEvicted() throws DeserializationException {
        // Given — server is initialised with one tool
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));
        awaitToolCount(serverId, 1);

        // When — server deleted event arrives
        notifier.handleMcpServerChanged(buildMcpServerChangedEvent(serverId, false, true));

        // Then — server is evicted; listToolsForServer returns empty (key removed from servers map)
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverId).collectList().block()).isEmpty()
            );
    }

    @Test
    void givenActiveServer_whenServerIsDisabled_thenServerIsEvicted() throws DeserializationException {
        // Given — server is initialised with one tool
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));
        awaitToolCount(serverId, 1);

        // When — server disabled event arrives
        notifier.handleMcpServerChanged(buildMcpServerChangedEvent(serverId, true, false));

        // Then — server is evicted
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverId).collectList().block()).isEmpty()
            );
    }

    @Test
    void givenActiveServer_whenServerIsUpdatedAndStillEnabled_thenNoEviction() throws Exception {
        // Given — server is initialised with one tool
        FlowWithSource v1 = flowRepository.create(GenericFlow.of(buildFlow(List.of(
            buildMcpTrigger("t1", serverId)
        ))));
        mcpServerHandlerTransport.getServerHandler(contextFor(serverId));
        awaitToolCount(serverId, 1);

        // When — an update event arrives for a server that is still enabled and not deleted
        clusterEventQueue.emit(new ClusterEvent(ClusterEvent.EventType.MCP_SERVER_CHANGED, LocalDateTime.now(), new McpServerClusterEventPayload(null, serverId, false, false).toJson()));

        // Then — server is not evicted; tool count is unchanged after the subscriber has had time to process
        Awaitility.await()
            .pollDelay(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                assertThat(mcpServerHandlerTransport.listToolsForServer(null, serverId).collectList().block()).hasSize(1)
            );
    }

    /**
     * Simulates what the flow broadcast queue delivers: a GenericFlow whose revision comes from
     * the FlowWithSource Java object (not from the stored YAML, which omits the revision field).
     */
    private static GenericFlow asQueueEvent(FlowWithSource flow, List<GenericTrigger> triggers) {
        return GenericFlow.builder()
            .id(flow.getId())
            .namespace(flow.getNamespace())
            .tenantId(flow.getTenantId())
            .revision(flow.getRevision())
            .disabled(flow.isDisabled())
            .triggers(triggers)
            .build();
    }

    private void awaitToolCount(String serverIdToCheck, int expectedCount) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                List<McpSchema.Tool> tools = mcpServerHandlerTransport
                    .listToolsForServer(null, serverIdToCheck)
                    .collectList()
                    .block();
                assertThat(tools).hasSize(expectedCount);
            });
    }

    private KestraMcpTransportContext contextFor(String server) {
        return KestraMcpTransportContext.builder()
            .tenantId(null)
            .serverId(server)
            .build();
    }

    private static GenericTrigger buildGenericMcpTrigger(String triggerId, String server) {
        GenericTrigger trigger = GenericTrigger.builder()
            .id(triggerId)
            .type(McpToolTrigger.class.getName())
            .build();
        trigger.setAdditionalProperty("mcpServer", server);
        return trigger;
    }

    private static McpToolTrigger buildMcpTrigger(String triggerId, String server) {
        return McpToolTrigger.builder()
            .id(triggerId)
            .type(McpToolTrigger.class.getName())
            .toolName(triggerId + "-tool")
            .description("description")
            .toolDescription("tool description")
            .title("title")
            .mcpServer(server)
            .build();
    }

    private Flow buildFlow(List<AbstractTrigger> triggers) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.mcp.test")
            .tasks(List.of(
                Return.builder()
                    .id("return")
                    .type(Return.class.getName())
                    .format(Property.ofValue("ok"))
                    .build()
            ))
            .triggers(triggers)
            .build();
    }

    private static Flow buildFlowWithSameId(FlowWithSource previous, List<AbstractTrigger> triggers) {
        return Flow.builder()
            .id(previous.getId())
            .namespace(previous.getNamespace())
            .tasks(List.of(
                Return.builder()
                    .id("return")
                    .type(Return.class.getName())
                    .format(Property.ofValue("ok"))
                    .build()
            ))
            .triggers(triggers)
            .build();
    }

    private static Flow buildDisabledFlowWithSameId(FlowWithSource previous, List<AbstractTrigger> triggers) {
        return buildFlowWithSameId(previous, triggers).toBuilder().disabled(true).build();
    }

    private static ClusterEvent buildMcpServerChangedEvent(String serverId, boolean disabled, boolean deleted) {
        return new ClusterEvent(ClusterEvent.EventType.MCP_SERVER_CHANGED, LocalDateTime.now(), new McpServerClusterEventPayload(null, serverId, deleted, disabled).toJson());
    }
}
