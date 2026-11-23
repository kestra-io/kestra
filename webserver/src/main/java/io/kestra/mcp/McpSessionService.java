package io.kestra.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.models.McpSessionEvent;
import io.kestra.core.mcp.models.McpSessionEvent.McpSessionEventType;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.ServerInstance;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks MCP sessions on this node and across the cluster.
 * <p>
 * A session belongs to the caller who initialized it. Every lookup made on behalf of a request goes
 * through the caller carried by its {@link KestraMcpTransportContext}, and a session owned by someone
 * else is reported as absent, so a caller cannot tell it apart from one that does not exist.
 */
@Slf4j
@Singleton
public class McpSessionService {
    private final McpSessionRepositoryInterface sessionRepository;
    private final BroadcastQueueInterface<McpSessionEvent> mcpSessionQueue;
    private final Map<KestraMcpTransportContext, LocalSession> streamableSessions = new ConcurrentHashMap<>();
    private QueueSubscriber<McpSessionEvent> sessionSubscriber;

    static final Duration SESSION_PURGE_AGE = Duration.ofHours(48);

    @Inject
    public McpSessionService(McpSessionRepositoryInterface sessionRepository, BroadcastQueueInterface<McpSessionEvent> mcpSessionQueue) {
        this.sessionRepository = sessionRepository;
        this.mcpSessionQueue = mcpSessionQueue;
    }

    @PostConstruct
    public void start() {
        int purged = sessionRepository.purgeOlderThan(Instant.now().minus(SESSION_PURGE_AGE));
        if (purged > 0) {
            log.info("Purged {} orphaned MCP sessions older than {}", purged, SESSION_PURGE_AGE);
        }

        sessionSubscriber = mcpSessionQueue.subscriber().subscribe(either ->
        {
            if (either.isRight()) {
                log.warn("Failed to deserialize MCP session event: {}", either.getRight().getMessage());
                return;
            }
            McpSessionEvent event = either.getLeft();
            McpSession session = event.session();
            boolean isNewOwner = McpSessionEventType.OWNERSHIP_CHANGED == event.type()
                && ServerInstance.INSTANCE_ID.equals(session.sseNode());
            if (!isNewOwner && (event.type() == McpSessionEventType.DELETED || event.type() == McpSessionEventType.OWNERSHIP_CHANGED)) {
                KestraMcpTransportContext context = KestraMcpTransportContext.builder()
                    .tenantId(session.tenantId())
                    .serverId(session.serverId())
                    .sessionId(session.sessionId())
                    .build();
                Optional.ofNullable(streamableSessions.remove(context))
                    .ifPresent(local -> local.session().delete());
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (sessionSubscriber != null) {
            sessionSubscriber.close();
        }
    }

    public Collection<McpStreamableServerSession> listMcpStreamableServerSession() {
        return streamableSessions.values().stream().map(LocalSession::session).toList();
    }

    public void clear() {
        streamableSessions.clear();
    }

    /**
     * Returns true if the session exists in the repository (on any node) and belongs to the caller.
     */
    public boolean sessionExists(KestraMcpTransportContext context) {
        return findOwnedSession(context).isPresent();
    }

    /**
     * Returns the session held on this node, provided it belongs to the caller.
     */
    Optional<McpStreamableServerSession> findMcpStreamableServerSession(KestraMcpTransportContext context) {
        return Optional.ofNullable(streamableSessions.get(context))
            .filter(local -> isOwner(local.ownerId(), context))
            .map(LocalSession::session);
    }

    /**
     * Registers a newly-initialized session on this node, persisting it to the repository
     * and adding it to the local {@code streamableSessions} map.
     */
    void addProxyForMcpStreamableServerSession(KestraMcpTransportContext context, McpStreamableServerSession mcpStreamableServerSession) {
        if (sessionRepository.find(context.getTenantId(), context.getServerId(), context.getSessionId()).isPresent()) {
            throw new KestraRuntimeException("Unable to add session proxy as one is already registered for session: " + context.getSessionId());
        }
        McpSession session = new McpSession(
            context.getTenantId(), context.getServerId(),
            context.getSessionId(), ServerInstance.INSTANCE_ID, context.getUserId(), false
        );
        sessionRepository.save(session);
        streamableSessions.put(context, new LocalSession(mcpStreamableServerSession, context.getUserId()));
        emit(new McpSessionEvent(session, McpSessionEventType.CREATED));
    }

    /**
     * Takes over the SSE ownership of a session that is currently registered on another node.
     * The caller must already have been checked as the session's owner, see {@link #sessionExists}.
     * <p>
     * Updates {@code sseNode} in the repository to point to this instance, registers the
     * supplied session locally, and broadcasts the change so the previous owning node can
     * release its in-memory state.
     * <p>
     * Call {@link #deregisterSseSession(KestraMcpTransportContext)} when the SSE connection closes.
     *
     * @param context the transport context identifying the session
     * @param session the local session that will serve the SSE stream
     */
    public void takeSseOwnership(KestraMcpTransportContext context, McpStreamableServerSession session) {
        McpSession updated = new McpSession(
            context.getTenantId(), context.getServerId(),
            context.getSessionId(), ServerInstance.INSTANCE_ID, context.getUserId(), false
        );
        sessionRepository.save(updated);
        streamableSessions.put(context, new LocalSession(session, context.getUserId()));
        emit(new McpSessionEvent(updated, McpSessionEventType.OWNERSHIP_CHANGED));
    }

    /**
     * Removes a session from the local {@code streamableSessions} map when its SSE connection
     * closes. Does not delete the session from the repository — the session remains valid until
     * the client explicitly sends a {@code DELETE} request.
     *
     * @param context the transport context identifying the session
     */
    public void deregisterSseSession(KestraMcpTransportContext context) {
        streamableSessions.remove(context);
    }

    /**
     * Deletes the session from the repository and broadcasts a delete signal so all nodes
     * (including the SSE-owning node) can clean up their local {@code streamableSessions} entry.
     *
     * @param context the transport context identifying the session to close
     * @return false when no session owned by the caller matched, so nothing was closed
     */
    public boolean close(KestraMcpTransportContext context) {
        Optional<McpSession> owned = findOwnedSession(context);
        owned.ifPresent(session ->
        {
            sessionRepository.delete(context.getTenantId(), context.getServerId(), context.getSessionId());
            emit(new McpSessionEvent(session, McpSessionEventType.DELETED));
        });
        return owned.isPresent();
    }

    private Optional<McpSession> findOwnedSession(KestraMcpTransportContext context) {
        return sessionRepository.find(context.getTenantId(), context.getServerId(), context.getSessionId())
            .filter(session -> isOwner(session.userId(), context));
    }

    /**
     * An anonymous caller (on a public server) owns the sessions it opened anonymously; those are
     * protected only by their server-generated id.
     */
    private static boolean isOwner(@Nullable String ownerId, KestraMcpTransportContext context) {
        return Objects.equals(ownerId, context.getUserId());
    }

    private void emit(McpSessionEvent event) {
        try {
            mcpSessionQueue.emit(event);
        } catch (QueueException e) {
            log.error("Failed to emit MCP session event {} for session {}: {}", event.type(), event.session().sessionId(), e.getMessage(), e);
        }
    }

    private record LocalSession(McpStreamableServerSession session, @Nullable String ownerId) {
    }
}
