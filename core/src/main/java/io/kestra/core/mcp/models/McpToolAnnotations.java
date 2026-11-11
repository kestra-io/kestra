package io.kestra.core.mcp.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tool behaviour hints following the MCP annotation specification.
 * These flags inform AI agents about the side effects and safety characteristics of a tool.
 */
@Schema(description = "Tool behaviour hints following the MCP annotation specification.")
public record McpToolAnnotations(
    @Schema(
        title = "Whether this tool is read-only.",
        description = "Set to `true` if the tool does not modify any state or produce side effects. When `true`, `destructive` and `idempotent` are not meaningful."
    ) boolean readOnly,

    @Schema(
        title = "Whether this tool may interact with external entities beyond its closed domain.",
        description = "Set to `true` if the tool reaches external systems such as third-party APIs, databases, or file systems outside the Kestra environment."
    ) boolean openWorld,

    @Schema(
        title = "Whether this tool may perform destructive updates.",
        description = "Only meaningful when `readOnly` is `false`. Set to `false` for tools whose effects can be safely undone or are non-destructive (e.g., creating a record). Default is `true`."
    ) boolean destructive,

    @Schema(
        title = "Whether calling this tool repeatedly with the same arguments has the same effect as calling it once.",
        description = "Only meaningful when `readOnly` is `false`. Set to `true` for tools whose repeated invocation produces no additional side effects beyond the first call."
    ) boolean idempotent,

    @Schema(
        title = "Whether the tool result should be returned directly to the user without further AI processing.",
        description = "When `true`, the AI agent forwards the raw tool output to the user rather than interpreting or summarising it."
    ) boolean returnDirect) {

    /** Reads Kestra's own state and changes nothing. */
    public static final McpToolAnnotations READ_ONLY = new McpToolAnnotations(true, false, false, true, false);

    /** May change or delete state, including outside Kestra: the MCP specification's defaults for an unannotated tool. */
    public static final McpToolAnnotations DESTRUCTIVE = new McpToolAnnotations(false, true, true, false, false);
}
