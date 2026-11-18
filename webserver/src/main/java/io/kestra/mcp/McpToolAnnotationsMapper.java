package io.kestra.mcp;

import io.kestra.core.mcp.models.McpToolAnnotations;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Converts {@link McpToolAnnotations} to the form MCP clients receive, for every kind of tool source.
 */
public final class McpToolAnnotationsMapper {
    private McpToolAnnotationsMapper() {
    }

    public static McpSchema.ToolAnnotations toToolAnnotations(McpToolAnnotations annotations) {
        return new McpSchema.ToolAnnotations(
            null,
            annotations.readOnly(),
            annotations.destructive(),
            annotations.idempotent(),
            annotations.openWorld(),
            annotations.returnDirect()
        );
    }
}
