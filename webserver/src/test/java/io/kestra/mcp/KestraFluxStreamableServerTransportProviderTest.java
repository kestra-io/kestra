package io.kestra.mcp;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.models.McpSessionEvent;
import io.kestra.core.mcp.models.McpSessionEvent.McpSessionEventType;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.ServerInstance;

import io.micronaut.http.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

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
    void shouldRejectRequestWhenServerIsShuttingDown() {
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
    void shouldReturnMethodNotAllowedWhenUnsupportedHttpMethodReceived() {
        // Given — an unsupported HTTP method (PUT) with otherwise valid headers
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String> create(HttpMethod.PUT, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    void shouldRejectRequestWhenRequestHasInvalidResponseType() {
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
    void shouldRejectRequestWhenPostRequestHasEmptyBody() {
        // Given — POST with no body at all
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();
        MutableHttpRequest<String> request = HttpRequest.<String> create(HttpMethod.POST, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);

        // When
        HttpResponse<?> response = provider.handleRequest(request, context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldRejectRequestWhenPostRequestHasInvalidBody() {
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
    void shouldReturnSessionIdsWhenInitializeRequestReceived() {
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
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        assertThat(mcpSessionRepository.find(context.getTenantId(), context.getServerId(), sessionId)).isPresent();
    }

    @Test
    void shouldIssueNewSessionIdWhenInitializeCarriesClientSessionId() {
        // Given — the client tries to pick its own session id
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext clientChosen = buildTransportContext("user-a");
        HttpRequest<String> request = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, clientChosen.getSessionId());

        // When
        HttpResponse<?> response = provider.handleRequest(request, clientChosen).block();

        // Then — the server issues its own id and nothing is stored under the client's
        assertThat(response).isNotNull();
        String issued = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(issued).isNotBlank().isNotEqualTo(clientChosen.getSessionId());
        assertThat(mcpSessionRepository.find(clientChosen.getTenantId(), clientChosen.getServerId(), clientChosen.getSessionId())).isEmpty();
        assertThat(mcpSessionRepository.find(clientChosen.getTenantId(), clientChosen.getServerId(), issued))
            .get().extracting(McpSession::userId).isEqualTo("user-a");
    }

    @Test
    void shouldAcceptRequestWhenNotificationRequestReceived() {
        // Given — a session established via initialize, then a notification posted with the session ID
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = initialize(provider, buildTransportContext());

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
    void shouldAcceptRequestWhenPersistentSseConnectionIsOnSeparateInstance() {
        // Given
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(
            new McpSession(
                context.getTenantId(),
                context.getServerId(),
                context.getSessionId(),
                "other-server-instance-id",
                null, false
            )
        );

        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);

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
    void shouldHandleToolCallByEphemeralSessionWhenSseConnectionIsOnSeparateInstance() {
        // Given — session exists on another server
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(
            new McpSession(
                context.getTenantId(), context.getServerId(),
                context.getSessionId(), "other-server-instance-id", null, false
            )
        );

        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
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
    void shouldNotifyOnlyLocalSessionsWhenNotifyClientsIsCalled() {
        // Given — a local session initialized on this node
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        initialize(provider, buildTransportContext());

        // When — a notification is broadcast; should complete without error
        // (cross-node fanout was removed: each node handles its own sessions via the broadcast flow queue)
        provider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null).block();

        // Then — no exception; the local session count is still 1
        assertThat(mcpSessionService.listMcpStreamableServerSession()).hasSize(1);
    }

    @Test
    void shouldDeleteSessionAndEmitBroadcastWhenDeleteReceivedForRemoteSseSession() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(
            new McpSession(
                context.getTenantId(), context.getServerId(),
                context.getSessionId(), remoteSseNode, null, false
            )
        );

        // Subscribe to the broadcast queue before sending the DELETE
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft() && either.getLeft().session().sessionId().equals(context.getSessionId())) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);

        // When — DELETE arrives on this server for a session owned by another server
        HttpRequest<String> deleteRequest = HttpRequest.<String> create(HttpMethod.DELETE, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(deleteRequest, context).block();

        // Then — 200 OK
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the session is removed from the repository
        assertThat(
            mcpSessionRepository.find(
                context.getTenantId(), context.getServerId(), context.getSessionId()
            )
        ).isEmpty();

        // And — a delete signal was broadcast so all nodes can clean up their local state
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().session().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().type()).isEqualTo(McpSessionEventType.DELETED);

        subscriber.close();
    }

    @Test
    void shouldTransferSseOwnershipToThisNodeWhenGetReceivedForRemoteSseSession() throws InterruptedException {
        // Given — session exists on another server
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext context = buildTransportContext();
        mcpSessionRepository.save(
            new McpSession(
                context.getTenantId(), context.getServerId(),
                context.getSessionId(), remoteSseNode, null, false
            )
        );

        // Subscribe to the broadcast queue before sending the GET
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft() && either.getLeft().session().sessionId().equals(context.getSessionId())) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — GET arrives on this server for a session owned by another server
        HttpRequest<String> getRequest = HttpRequest.<String> GET("/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, context.getSessionId());
        HttpResponse<?> response = provider.handleRequest(getRequest, context).block();

        // Then — SSE stream is established on this server (200 OK, not 404)
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // And — the repository now records this node as the SSE owner
        assertThat(
            mcpSessionRepository.find(
                context.getTenantId(), context.getServerId(), context.getSessionId()
            )
        ).isPresent().get().extracting(McpSession::sseNode).isEqualTo(ServerInstance.INSTANCE_ID);

        // And — a broadcast was emitted so the previous owning node can release its in-memory state
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().session().sessionId()).isEqualTo(context.getSessionId());
        assertThat(received.get().session().sseNode()).isEqualTo(ServerInstance.INSTANCE_ID);
        assertThat(received.get().type()).isEqualTo(McpSessionEventType.OWNERSHIP_CHANGED);

        subscriber.close();
    }

    @Test
    void shouldRemoveSessionFromLocalStateWhenOwnershipTransferBroadcastReceived() throws Exception {
        // Given — a session initialized on this node (ends up in streamableSessions)
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = initialize(provider, buildTransportContext());

        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        // When — another node broadcasts that it has taken ownership of the session
        mcpSessionQueue.emit(
            new McpSessionEvent(
                new McpSession(context.getTenantId(), context.getServerId(), context.getSessionId(), "other-node", null, false),
                McpSessionEventType.OWNERSHIP_CHANGED
            )
        );

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
    void shouldRemoveSessionFromLocalStateWhenDeleteBroadcastReceived() throws Exception {
        // Given — a session initialized on this node
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = initialize(provider, buildTransportContext());

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
    void shouldBroadcastCreatedEventWhenSessionInitialized() throws InterruptedException {
        // Given — subscribe to the queue before the session is created; the server is unique to this
        // test, since the session id is only known once initialize has returned
        KestraMcpTransportContext requestContext = buildTransportContext().toBuilder()
            .serverId("server-" + UUID.randomUUID())
            .build();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<McpSessionEvent> received = new AtomicReference<>();
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either ->
        {
            if (
                either.isLeft()
                    && either.getLeft().session().serverId().equals(requestContext.getServerId())
                    && either.getLeft().type() == McpSessionEventType.CREATED
            ) {
                received.set(either.getLeft());
                latch.countDown();
            }
        });

        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — an initialize request creates a new session
        KestraMcpTransportContext context = initialize(provider, requestContext);

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
    void shouldKeepSessionInLocalStateWhenOwnershipChangedToThisNode() throws InterruptedException, QueueException {
        // Given — a session initialized on this node (ends up in streamableSessions)
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext context = initialize(provider, buildTransportContext());
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        CountDownLatch latch = new CountDownLatch(1);
        QueueSubscriber<McpSessionEvent> subscriber = mcpSessionQueue.subscriber().subscribe(either ->
        {
            if (
                either.isLeft()
                    && either.getLeft().session().sessionId().equals(context.getSessionId())
                    && either.getLeft().type() == McpSessionEventType.OWNERSHIP_CHANGED
            ) {
                latch.countDown();
            }
        });

        // When — an OWNERSHIP_CHANGED event arrives with this node as the new SSE owner
        mcpSessionQueue.emit(
            new McpSessionEvent(
                new McpSession(context.getTenantId(), context.getServerId(), context.getSessionId(), ServerInstance.INSTANCE_ID, null, false),
                McpSessionEventType.OWNERSHIP_CHANGED
            )
        );

        // Then — the subscriber recognises this node is the new owner and does NOT remove the session
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mcpSessionService.findMcpStreamableServerSession(context)).isPresent();

        subscriber.close();
    }

    @Test
    void shouldReturnNotFoundWhenAnotherCallerPostsToSession() {
        // Given — user A holds a session on this node
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext owner = initialize(provider, buildTransportContext("user-a"));
        KestraMcpTransportContext intruder = owner.toBuilder().userId("user-b").build();

        // When — user B sends a request with A's session id
        HttpResponse<?> response = provider.handleRequest(mcpPost(TOOLS_LIST_REQUEST, owner.getSessionId()), intruder).block();

        // Then — B is told the session does not exist, and A can still use it
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        HttpResponse<?> ownerResponse = provider.handleRequest(mcpPost(NOTIFICATION_REQUEST, owner.getSessionId()), owner).block();
        assertThat(ownerResponse).isNotNull();
        assertThat(ownerResponse.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenAnotherCallerOpensSseStreamOnSession() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext owner = initialize(provider, buildTransportContext("user-a"));
        KestraMcpTransportContext intruder = owner.toBuilder().userId("user-b").build();

        // When
        HttpResponse<?> response = provider.handleRequest(sseGet(owner.getSessionId()), intruder).block();

        // Then — no stream is handed to B, and A's session is untouched
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(mcpSessionService.findMcpStreamableServerSession(owner)).isPresent();
        assertThat(mcpSessionRepository.find(owner.getTenantId(), owner.getServerId(), owner.getSessionId()))
            .get().extracting(McpSession::sseNode).isEqualTo(ServerInstance.INSTANCE_ID);
    }

    @Test
    void shouldReturnNotFoundAndKeepSessionWhenAnotherCallerDeletesSession() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());
        KestraMcpTransportContext owner = initialize(provider, buildTransportContext("user-a"));
        KestraMcpTransportContext intruder = owner.toBuilder().userId("user-b").build();

        // When
        HttpResponse<?> response = provider.handleRequest(mcpDelete(owner.getSessionId()), intruder).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(mcpSessionRepository.find(owner.getTenantId(), owner.getServerId(), owner.getSessionId())).isPresent();
        assertThat(mcpSessionService.findMcpStreamableServerSession(owner)).isPresent();
    }

    @Test
    void shouldReturnNotFoundWhenAnotherCallerUsesSessionHeldOnAnotherNode() {
        // Given — user A's session lives on another node, so only the repository knows about it
        String remoteSseNode = UUID.randomUUID().toString();
        KestraMcpTransportContext owner = buildTransportContext("user-a");
        mcpSessionRepository.save(
            new McpSession(owner.getTenantId(), owner.getServerId(), owner.getSessionId(), remoteSseNode, "user-a", false)
        );
        KestraMcpTransportContext intruder = owner.toBuilder().userId("user-b").build();
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        provider.setSessionFactory(buildSessionFactory());

        // When — user B tries every cross-node path
        HttpResponse<?> post = provider.handleRequest(mcpPost(TOOLS_LIST_REQUEST, owner.getSessionId()), intruder).block();
        HttpResponse<?> get = provider.handleRequest(sseGet(owner.getSessionId()), intruder).block();
        HttpResponse<?> delete = provider.handleRequest(mcpDelete(owner.getSessionId()), intruder).block();

        // Then — all are refused, and B took neither the stream nor the session
        assertThat(post).isNotNull().extracting(r -> r.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(get).isNotNull().extracting(r -> r.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(delete).isNotNull().extracting(r -> r.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(mcpSessionRepository.find(owner.getTenantId(), owner.getServerId(), owner.getSessionId()))
            .get().extracting(McpSession::sseNode).isEqualTo(remoteSseNode);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownSession() {
        // Given
        KestraFluxStreamableServerTransportProvider provider = new KestraFluxStreamableServerTransportProvider(new McpErrorResponseMapper(), mcpSessionService);
        KestraMcpTransportContext context = buildTransportContext();

        // When
        HttpResponse<?> response = provider.handleRequest(mcpDelete(context.getSessionId()), context).block();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    /** Initializes a session and returns the context carrying the session id the server issued. */
    private static KestraMcpTransportContext initialize(KestraFluxStreamableServerTransportProvider provider, KestraMcpTransportContext context) {
        HttpRequest<String> initRequest = HttpRequest.POST("/mcp", toJson(INITIALIZE_REQUEST))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE);
        HttpResponse<?> response = provider.handleRequest(initRequest, context).block();
        assertThat(response).isNotNull();
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return context.toBuilder().sessionId(sessionId).build();
    }

    private static HttpRequest<String> mcpPost(McpSchema.JSONRPCMessage message, String sessionId) {
        return HttpRequest.POST("/mcp", toJson(message))
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    private static HttpRequest<String> sseGet(String sessionId) {
        return HttpRequest.<String> GET("/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    private static HttpRequest<String> mcpDelete(String sessionId) {
        return HttpRequest.<String> create(HttpMethod.DELETE, "/mcp")
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    private static KestraMcpTransportContext buildTransportContext() {
        return buildTransportContext(null);
    }

    private static KestraMcpTransportContext buildTransportContext(String userId) {
        return KestraMcpTransportContext.builder()
            .tenantId("test-tenant")
            .serverId("default")
            .sessionId(UUID.randomUUID().toString())
            .userId(userId)
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
        return initRequest ->
        {
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
