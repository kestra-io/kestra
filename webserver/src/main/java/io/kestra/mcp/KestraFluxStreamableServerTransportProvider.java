package io.kestra.mcp;

import io.micronaut.http.*;
import io.micronaut.http.sse.Event;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.*;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.util.KeepAliveScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class KestraFluxStreamableServerTransportProvider implements McpStreamableServerTransportProvider {

    private static final Logger logger = LoggerFactory.getLogger(KestraFluxStreamableServerTransportProvider.class);

    public static final String MESSAGE_EVENT_TYPE = "message";

    private static final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    private McpStreamableServerSession.Factory sessionFactory;

    private final McpSessionService mcpSessionService;

    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(30);

    private final McpErrorResponseMapper mcpErrorResponseMapper;

    private final KeepAliveScheduler keepAliveScheduler;



    public KestraFluxStreamableServerTransportProvider(McpErrorResponseMapper mcpErrorResponseMapper, McpSessionService mcpSessionService) {
        this.mcpErrorResponseMapper = mcpErrorResponseMapper;
        this.mcpSessionService = mcpSessionService;
        this.keepAliveScheduler = KeepAliveScheduler
            .builder(() -> (isClosing.get()) ? Flux.empty() : Flux.fromIterable(this.mcpSessionService.listMcpStreamableServerSession()))
            .initialDelay(KEEP_ALIVE_INTERVAL)
            .interval(KEEP_ALIVE_INTERVAL)
            .build();
        this.keepAliveScheduler.start();
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        logger.debug("Attempting to broadcast message to {} local session(s)", mcpSessionService.listMcpStreamableServerSession().size());

        return Flux.fromIterable(mcpSessionService.listMcpStreamableServerSession())
            .flatMap(session -> session.sendNotification(method, params)
                .doOnError(e -> logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage()))
                .onErrorComplete())
            .then();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.defer(() -> {
            this.isClosing.set(true);
            return Flux.fromIterable(mcpSessionService.listMcpStreamableServerSession())
                .doFirst(() -> logger.debug("Initiating graceful shutdown with {} active sessions", mcpSessionService.listMcpStreamableServerSession().size()))
                .flatMap(McpStreamableServerSession::closeGracefully)
                .then();
        }).then().doOnSuccess(v -> {
            mcpSessionService.clear();
            this.keepAliveScheduler.shutdown();
        });
    }

    @Override
    public List<String> protocolVersions() {
        return List.of(ProtocolVersions.MCP_2024_11_05, ProtocolVersions.MCP_2025_03_26,
            ProtocolVersions.MCP_2025_06_18);
    }

    public Mono<? extends HttpResponse<?>> handleRequest(HttpRequest<String> request, KestraMcpTransportContext transportContext) {
        if (isClosing.get()) {
            return Mono.just(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Flux.just(Event.of("Server is shutting down, no new requests are accepted"))));
        }


        List<MediaType> acceptHeaders = request.getHeaders().accept();
        if (!(
            acceptHeaders.contains(MediaType.TEXT_EVENT_STREAM_TYPE) &&
                (acceptHeaders.contains(MediaType.APPLICATION_JSON_TYPE) || request.getMethod() == HttpMethod.GET)
        )) {
            return Mono.just(HttpResponse.badRequest());
        }

        return switch (request.getMethod()) {
            case POST -> handlePost(request, transportContext);
            case GET -> handleGet(request, transportContext);
            case DELETE -> handleDelete(request, transportContext);
            default -> Mono.just(HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED));
        };
    }

    private Mono<MutableHttpResponse<?>> handleGet(HttpRequest<String> request, KestraMcpTransportContext transportContext) {
        String sessionId = request.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            return buildErrorResponse(
                "Missing session ID in request headers.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.INVALID_REQUEST
            );
        }

        Optional<McpStreamableServerSession> session = mcpSessionService.findMcpStreamableServerSession(transportContext);
        if (session.isEmpty()) {
            // The session may be owned by another node. Take over SSE ownership so that
            // server-to-client notifications are routed to this instance going forward.
            boolean sessionExistsOnAnotherNode = mcpSessionService.sessionExists(transportContext);
            if (!sessionExistsOnAnotherNode) {
                return buildErrorResponse(
                    "Session not found.",
                    HttpStatus.NOT_FOUND,
                    McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                );
            }
            if (sessionFactory == null) {
                return buildErrorResponse(
                    "No session factory configured for this server instance.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    McpSchema.ErrorCodes.INTERNAL_ERROR
                );
            }
            McpStreamableServerSession takenSession = sessionFactory.startSession(
                new McpSchema.InitializeRequest(
                    ProtocolVersions.MCP_2025_03_26,
                    new McpSchema.ClientCapabilities(null, null, null, null),
                    new McpSchema.Implementation("kestra-proxy-client", "1.0.0")
                )
            ).session();
            mcpSessionService.takeSseOwnership(transportContext, takenSession);
            session = Optional.of(takenSession);
        }

        final McpStreamableServerSession resolvedSession = session.get();
        Flux<Event<?>> serverSentEvent = Flux.<Event<?>>create(sink -> {
            FluxStreamableMcpSessionTransport sessionTransport = new FluxStreamableMcpSessionTransport(sink);
            McpStreamableServerSession.McpStreamableServerSessionStream listeningStream = resolvedSession
                .listeningStream(sessionTransport);
            sink.onDispose(() -> {
                listeningStream.close();
                mcpSessionService.deregisterSseSession(transportContext);
            });
        }).contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext));

        return Mono.just(HttpResponse.ok().body(
            serverSentEvent
        ));
    }

    private Mono<? extends HttpResponse<?>> handlePost(HttpRequest<String> request, KestraMcpTransportContext transportContext) {
        Optional<String> body = request.getBody();

        if (body.isEmpty()) {
            return buildErrorResponse(
                "Request body is empty.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.INVALID_REQUEST
            );
        }

        McpSchema.JSONRPCMessage message;
        try {
            message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body.get());
        } catch (IOException e) {
            return buildErrorResponse(
                "Unable to parse JSONRPCMessage.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.PARSE_ERROR
            );
        }

        if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest
            && jsonrpcRequest.method().equals(McpSchema.METHOD_INITIALIZE)) {
            var typeReference = new TypeRef<McpSchema.InitializeRequest>() {
            };
            McpSchema.InitializeRequest initializeRequest = jsonMapper.convertValue(jsonrpcRequest.params(),
                typeReference);
            McpStreamableServerSession.McpStreamableServerSessionInit init = this.sessionFactory
                .startSession(initializeRequest);
            mcpSessionService.addProxyForMcpStreamableServerSession(transportContext, init.session());
            return init.initResult().map(initializeResult ->
                new McpSchema.JSONRPCResponse(
                    McpSchema.JSONRPC_VERSION,
                    jsonrpcRequest.id(),
                    initializeResult,
                    null
                )
            ).map(response -> HttpResponse.ok()
                .header(HttpHeaders.MCP_SESSION_ID, transportContext.getSessionId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response)
            );
        }

        String sessionId = request.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            return buildErrorResponse(
                "Missing session ID in request headers.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.INVALID_REQUEST
            );
        }

        Optional<McpStreamableServerSession> session = mcpSessionService.findMcpStreamableServerSession(transportContext);
        if (session.isEmpty()) {
            // The session may be owned by a different server instance. Check the repository
            // to distinguish "session exists elsewhere" from "session does not exist at all".
            boolean sessionExistsOnAnotherNode = mcpSessionService.sessionExists(transportContext);
            if (!sessionExistsOnAnotherNode) {
                return buildErrorResponse(
                    "Session not found.",
                    HttpStatus.NOT_FOUND,
                    McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                );
            }

            if (message instanceof McpSchema.JSONRPCNotification) {
                return Mono.just(HttpResponse.accepted());
            }
            // Tool handlers are identical on every node — create a lightweight ephemeral session
            // to process the request and stream the result back in this POST response body.
            if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
                if (sessionFactory == null) {
                    return buildErrorResponse(
                        "No session factory configured for this server instance.",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        McpSchema.ErrorCodes.INTERNAL_ERROR
                    );
                }
                McpStreamableServerSession ephemeralSession = sessionFactory.startSession(
                    new McpSchema.InitializeRequest(
                        ProtocolVersions.MCP_2025_03_26,
                        new McpSchema.ClientCapabilities(null, null, null, null),
                        new McpSchema.Implementation("kestra-proxy-client", "1.0.0")
                    )
                ).session();
                return Mono.just(HttpResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(buildSeeBody(ephemeralSession, jsonrpcRequest)
                        .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
                    ));
            }
            return buildErrorResponse(
                "Unexpected message type for cross-server session.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.INVALID_REQUEST
            );
        }


        Mono<HttpResponse<?>> response = switch (message) {
            case McpSchema.JSONRPCResponse jsonrpcResponse ->
                session.get().accept(jsonrpcResponse).then(Mono.just(HttpResponse.accepted()));
            case McpSchema.JSONRPCNotification jsonrpcNotification ->
                session.get().accept(jsonrpcNotification).then(Mono.just(HttpResponse.accepted()));
            case McpSchema.JSONRPCRequest jsonrpcRequest -> Mono.just( HttpResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(buildSeeBody(session.get(), jsonrpcRequest)
                    .contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
                ));
        };

        return response
            .onErrorResume(McpError.class, e -> mcpErrorResponseMapper.exceptionResponse(e, message))
            .onErrorResume(Throwable.class, e -> mcpErrorResponseMapper.exceptionResponse(e, message));
    }

    private Flux<Event<?>> buildSeeBody(McpStreamableServerSession session, McpSchema.JSONRPCRequest jsonrpcRequest) {
        return Flux.create(sink -> {
            FluxStreamableMcpSessionTransport st = new FluxStreamableMcpSessionTransport(sink);
            Mono<Void> stream = session.responseStream(jsonrpcRequest, st);
            Disposable streamSubscription = stream.onErrorComplete(err -> {
                sink.error(err);
                return true;
            }).contextWrite(sink.contextView()).subscribe();
            sink.onCancel(streamSubscription);
        });
    }


    private Mono<MutableHttpResponse<?>> handleDelete(HttpRequest<String> request, KestraMcpTransportContext transportContext) {
        if (!request.getHeaders().contains(HttpHeaders.MCP_SESSION_ID)) {
            return Mono.just(HttpResponse.badRequest().body("Bad request: Mcp-Session-Id header required."));
        }

        String sessionId = request.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            return buildErrorResponse(
                "Missing session ID in request headers.",
                HttpStatus.BAD_REQUEST,
                McpSchema.ErrorCodes.INVALID_REQUEST
            );
        }

        mcpSessionService.close(transportContext);

        return Mono.just(HttpResponse.ok());
    }

    private Mono<MutableHttpResponse<?>> buildErrorResponse(String message, HttpStatus status, int code) {
        return Mono.just(
            HttpResponse.status(status).body(
                new McpSchema.JSONRPCResponse.JSONRPCError(
                    code,
                    message,
                    null
                )
            )
        );
    }



    public class FluxStreamableMcpSessionTransport implements McpStreamableServerTransport {
        private final FluxSink<Event<?>> sink;

        public FluxStreamableMcpSessionTransport(FluxSink<Event<?>> sink) {
            this.sink = sink;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return this.sendMessage(message, null);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
            return Mono.fromSupplier(() -> {
                try {
                    return jsonMapper.writeValueAsString(message);
                }
                catch (IOException e) {
                    throw Exceptions.propagate(e);
                }
            }).doOnNext(jsonText -> {
                Event<String> event = Event.of(jsonText)
                    .name(MESSAGE_EVENT_TYPE)
                    .id(messageId);
                sink.next(event);
            }).doOnError(e -> {
                Throwable exception = Exceptions.unwrap(e);
                sink.error(exception);
            }).then();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(sink::complete);
        }

        @Override
        public void close() {
            sink.complete();
        }

    }
}
