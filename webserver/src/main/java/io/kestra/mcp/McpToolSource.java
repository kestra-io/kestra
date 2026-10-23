package io.kestra.mcp;

/**
 * Supplies the tools exposed by every MCP server of one {@link Kind}.
 * <p>
 * The transport, sessions and endpoint are shared by every kind: a source only decides which tools a caller
 * sees on a server, and how callers that see the same tools are grouped so they share one built server.
 */
public interface McpToolSource {
    /**
     * @return the kind of server this source supplies tools for; at most one source may be registered per kind
     */
    Kind kind();

    /**
     * Resolves what the caller behind this context sees. Called once per request, while the caller is known.
     */
    McpToolView view(KestraMcpTransportContext context);

    enum Kind {
        /** Stored servers, exposing the flows whose {@code McpToolTrigger} targets them. */
        FLOWS,
        /** The server defined by configuration that exposes Kestra's own platform tools. */
        PLATFORM
    }
}
