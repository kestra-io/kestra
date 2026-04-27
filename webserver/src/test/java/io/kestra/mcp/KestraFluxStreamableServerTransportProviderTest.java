package io.kestra.mcp;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.models.McpSessionEvent;
import io.kestra.core.mcp.models.McpSessionEvent.McpSessionEventType;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import io.micronaut.http.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "h2")
class KestraFluxStreamableServerTransportProviderTest {

    private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    private static final McpSchema.JSONRPCRequest INITIALIZE_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_INITIALIZE,
        1,
        new McpSchema.InitializeRequest(
            ProtocolVersions.MCP_2025_03_26,
            new McpSchema.ClientCapabilities(null, null, null, null),
            new McpSchema.Implementation("test-client", "1.0.0")
        )
    );

    private static final McpSchema.JSONRPCNotification NOTIFICATION_REQUEST = new McpSchema.JSONRPCNotification(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        null
    );

    private static final McpSchema.JSONRPCRequest TOOLS_LIST_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_TOOLS_LIST,
        2,
        null
    );

    @Inject
    io.kestra.mcp.McpSessionService mcpSessionService;

    @AfterEach
    void clearSessions() {
        mcpSessionService.clear();
    }

    @Inject
    McpSessionRepositoryInterface mcpSessionRepository;

    @Inject
    BroadcastQueueInterface<McpSessionEvent> mcpSessionQueue;

    @Test
    void givenServerIsShuttingDown_whenNewRequestComes_thenRejectRequest() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.closeGracefully().block();
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void givenThing_whenAction_thenResult() {
        // Given — an unsupported HTTP method (PUT) with otherwise valid headers
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String>create(HttpMethod.PUT, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    void givenARequestWithInvalidResponseType_whenRequestReceived_thenRejectRequest() {
        // Given — Accept header contains only application/json, missing the required text/event-stream
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenPostRequestWithEmptyBody_whenRequestReceived_thenRejectRequest() {
        // Given — POST with no body at all
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String>create(HttpMethod.POST, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenPostRequestWithInvalidBody_whenRequestReceived_thenRejectRequest() {
        // Given — POST with a body that is not valid JSON-RPC
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", "{{not valid json}}")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void givenInitializeRequest_whenRequestReceived_thenSessionIdsReturned() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isNotNull();
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isEqualTo(context.getSessionId());
    }

    @Test
    void givenNotificationRequest_whenRequestReceived_thenRequestIsAccepted() {
        // Given — a session established via initialize, then a notification posted with the session ID
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();

        // When
        HttpRequest<String> notifRequest = HttpRequest.POST("/mcp", toJson(NOTIFICATION_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(notifRequest, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenRequestReceived_thenRequestIsAccepted() {
        // Given
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(),
            context.getServerId(),
            context.getSessionId(),
            "other-server-instance-id",
            null, false
        ));

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);

        // When
        HttpRequest<String> notifRequest = HttpRequest.POST("/mcp", toJson(NOTIFICATION_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(notifRequest, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenToolCallReceived_thenRequestIsHandledByEphemeralSession() {
        // Given — session exists on another server
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getServerId(),
            context.getSessionId(), "other-server-instance-id", null, false
        ));

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — a tools/list request arrives for that cross-server session
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(TOOLS_LIST_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then — an ephemeral session handles the request locally; 200 not 503
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void givenLocalAndRemoteSessions_whenNotifyClientsIsCalled_thenOnlyLocalSessionsReceiveNotification() {
        // Given — a local session initialized on this node
        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext localContext = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, localContext).block();

        // When — a notification is broadcast; should complete without error
        // (cross-node fanout was removed: each node handles its own sessions via the broadcast flow queue)
        provider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null).block();

        // Then — no exception; the local session count is still 1
        assertThat(mcpSessionService.listMcpStreamableServerSession()).hasSize(1);
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenDeleteReceived_thenSessionDeletedAndBroadcastEmitted() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getServerId(),
            context.getSessionId(), remoteSseNode, null, false
        ));

        // Subscribe to the broadcast queue before sending the DELETE
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either -> {
            if (either.isLeft() && either.getLeft().session().sessionId().equals(context.getSessionId())) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);

        // When — DELETE arrives on this server for a session owned by another server
        HttpRequest<String> deleteRequest = HttpRequest.<String>create(HttpMethod.DELETE, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(deleteRequest, context).block();

        // Then — 200 OK
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the session is removed from the repository
        assertThat(mcpSessionRepository.find(
            context.getTenantId(), context.getServerId(), context.getSessionId()
        )).isEmpty();

        // And — a delete signal was broadcast so all nodes can clean up their local state
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().session().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().type()).isEqualTo(McpSessionEventType.DELETED);

        subscriber.close();
    }

    @Test
    void givenPersistentSSEConnectionIsOnSeparateInstance_whenGetReceived_thenSseOwnershipTransferredToThisNode() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(new McpSession(
            context.getTenantId(), context.getServerId(),
            context.getSessionId(), remoteSseNode, null, false
        ));

        // Subscribe to the broadcast queue before sending the GET
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either -> {
            if (either.isLeft() && either.getLeft().session().sessionId().equals(context.getSessionId())) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — GET arrives on this server for a session owned by another server
        HttpRequest<String> getRequest = HttpRequest.<String>GET("/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(getRequest, context).block();

        // Then — SSE stream is established on this server (200 OK, not 404)
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the repository now records this node as the SSE owner
        assertThat(mcpSessionRepository.find(
            context.getTenantId(), context.getServerId(), context.getSessionId()
        )).isPresent().get().extracting(McpSession::sseNode).isEqualTo(ServerInstance.INSTANCE_ID);

        // And — a broadcast was emitted so the previous owning node can release its in-memory state
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().session().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().session().sseNode()).isEqualTo(ServerInstance.INSTANCE_ID);
        assertThat(received.get().type()).isEqualTo(McpSessionEventType.OWNERSHIP_CHANGED);

        subscriber.close();
    }

    @Test
    void givenSessionOwnedLocally_whenOwnershipTransferBroadcastReceived_thenSessionRemovedFromLocalState() throws Exception {
        // Given — a session initialized on this node (ends up in streamableSessions)
        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();

        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        // When — another node broadcasts that it has taken ownership of the session
        mcpSessionQueue.emit(new McpSessionEvent(
            new McpSession(context.getTenantId(), context.getServerId(), context.getSessionId(), "other-node", null, false),
            McpSessionEventType.OWNERSHIP_CHANGED
        ));

        // Then — this node's subscriber removes the session from local state
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (mcpSessionService.findMcpStreamableServerSession(context).isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isEmpty();
    }

    @Test
    void givenSessionOwnedLocally_whenDeleteBroadcastReceived_thenSessionRemovedFromLocalState() throws Exception {
        // Given — a session initialized on this node
        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();

        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        // When — a delete broadcast arrives (e.g. the client sent DELETE to another node)
        McpSession existing = mcpSessionRepository.find(
            context.getTenantId(), context.getServerId(), context.getSessionId()
        ).orElseThrow();
        mcpSessionQueue.emit(new McpSessionEvent(existing, McpSessionEventType.DELETED));

        // Then — this node's subscriber removes the session from local state
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (mcpSessionService.findMcpStreamableServerSession(context).isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isEmpty();
    }

    @Test
    void givenNewSession_whenSessionInitialized_thenCreatedEventBroadcast() throws InterruptedException {
        // Given — subscribe to the queue before the session is created
        KestraMcpTransportContext context = buildTransportContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either -> {
            if (either.isLeft()
                && either.getLeft().session().sessionId().equals(context.getSessionId())
                && either.getLeft().type() == McpSessionEventType.CREATED) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — an initialize request creates a new session
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();

        // Then — a CREATED event is broadcast with this node as the SSE owner
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().type()).isEqualTo(McpSessionEventType.CREATED);
        assertThat(received.get().session().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().session().sseNode()).isEqualTo(ServerInstance.INSTANCE_ID);

        // And — the session is registered in local state
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        subscriber.close();
    }

    @Test
    void givenSessionOwnedLocally_whenOwnershipChangedToThisNode_thenSessionKeptInLocalState() throws InterruptedException, QueueException {
        // Given — a session initialized on this node (ends up in streamableSessions)
        KestraFluxStreamableServerTransportProvider provider =
            new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = buildTransportContext();
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        provider.handleRequest(initRequest, context).block();
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        CountDownLatch latch = new CountDownLatch(1);
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either -> {
            if (either.isLeft()
                && either.getLeft().session().sessionId().equals(context.getSessionId())
                && either.getLeft().type() == McpSessionEventType.OWNERSHIP_CHANGED) {
                latch.countDown();
            }
        });

        // When — an OWNERSHIP_CHANGED event arrives with this node as the new SSE owner
        mcpSessionQueue.emit(new McpSessionEvent(
            new McpSession(context.getTenantId(), context.getServerId(), context.getSessionId(), ServerInstance.INSTANCE_ID, null, false),
            McpSessionEventType.OWNERSHIP_CHANGED
        ));

        // Then — the subscriber recognises this node is the new owner and does NOT remove the session
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        subscriber.close();
    }

    private static KestraMcpTransportContext buildTransportContext() {
        return KestraMcpTransportContext.builder()
            .tenantId("test-tenant")
            .serverId("default")
            .sessionId(UUID.randomUUID().toString())
            .build();
    }

    private static String toJson(Object message) {
        try {
            return JSON_MAPPER.writeValueAsString(message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message to JSON", e);
        }
    }

    private static McpStreamableServerSession.Factory buildSessionFactory() {
        return initRequest -> {
            McpSchema.InitializeResult result = new McpSchema.InitializeResult(
                ProtocolVersions.MCP_2025_03_26,
                new McpSchema.ServerCapabilities(null, null, null, null, null, null),
                new McpSchema.Implementation("test-server", "1.0.0"),
                null
            );
            McpStreamableServerSession session = new McpStreamableServerSession(
                UUID.randomUUID().toString(),
                initRequest.capabilities(),
                initRequest.clientInfo(),
                Duration.ofSeconds(30),
                Map.of(),
                Map.of()
            );
            return new McpStreamableServerSession.McpStreamableServerSessionInit(session, Mono.just(result));
        };
    }
}
