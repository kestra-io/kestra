package io.kestra.mcp;

import io.kestra.core.events.Actor;

import io.micronaut.core.annotation.Nullable;

/**
 * Who sent an MCP request, resolved on the request thread.
 *
 * @param id the caller's id, or {@code null} when anonymous
 * @param username the name recorded in the {@code system.username} label, or {@code null} when there is none
 * @param actor who the request acts on behalf of in the audit log, or {@code null} when it is not audited
 */
public record McpCaller(@Nullable String id, @Nullable String username, @Nullable Actor actor) {
}
