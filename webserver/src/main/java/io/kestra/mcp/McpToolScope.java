package io.kestra.mcp;

import io.kestra.core.models.AccessScope;

/**
 * Reads the {@link AccessScope} an {@link McpToolAccessControl} grants a caller.
 */
public final class McpToolScope {
    private McpToolScope() {
    }

    /**
     * Whether the scope covers a namespace: the namespace itself or one of its dotted descendants, so a
     * grant on {@code team.a} does not cover the sibling {@code team.archive}.
     */
    public static boolean allows(AccessScope scope, String namespace) {
        return switch (scope.kind()) {
            case GLOBAL -> true;
            case DENY_ALL -> false;
            case NAMESPACES -> namespace != null && scope.namespaces().stream()
                .anyMatch(granted -> namespace.equals(granted) || namespace.startsWith(granted + "."));
        };
    }

    /**
     * Identifies every caller that sees the same tools, so they share one built MCP server instead of one
     * being built per user.
     */
    public static String cacheKey(AccessScope scope) {
        return switch (scope.kind()) {
            case GLOBAL -> "*";
            case DENY_ALL -> "-";
            case NAMESPACES -> String.join(",", scope.namespaces().stream().sorted().toList());
        };
    }
}
