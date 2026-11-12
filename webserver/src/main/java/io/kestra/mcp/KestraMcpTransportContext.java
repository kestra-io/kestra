package io.kestra.mcp;

import io.kestra.core.events.Actor;

import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The caller is deliberately left out of {@code equals}/{@code hashCode}: a session is identified by its
 * tenant, server and session id, and is looked up from paths that know nothing about who is calling.
 * Whether the caller may use a session is checked separately, against the owner recorded by
 * {@link McpSessionService}.
 * <p>
 * The caller is resolved on the request thread and carried here, because tool handlers run on a reactor thread
 * where the request is no longer in scope.
 */
@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = { "userId", "username", "actor" })
public class KestraMcpTransportContext implements McpTransportContext {
    private final String tenantId;
    private final String serverId;
    private final String userId;
    @Nullable
    private final String username;
    @Nullable
    private final Actor actor;
    private String sessionId;

    @Override
    public Object get(String key) {
        return switch (key) {
            case "tenantId" -> tenantId;
            case "serverId" -> serverId;
            case "sessionId" -> sessionId;
            case "userId" -> userId;
            default -> null;
        };
    }
}
