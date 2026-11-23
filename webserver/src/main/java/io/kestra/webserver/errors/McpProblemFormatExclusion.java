package io.kestra.webserver.errors;


import jakarta.inject.Singleton;

/**
 * The Model Context Protocol transport, whose errors are JSON-RPC 2.0 envelopes rather than problem documents.
 *
 * <p>Only the transport {@code /mcp/{serverId}} is excluded, where some routes legitimately answer with an
 * empty body and a JSON-RPC client would not know what to do with a problem document. The management API for
 * configuring MCP servers sits outside that namespace, at {@code /mcp-servers}, and reports errors like any
 * other Kestra endpoint.
 */
@Singleton
public class McpProblemFormatExclusion implements ProblemFormatExclusion {
    private static final String MCP_SEGMENT = "/mcp/";

    @Override
    public boolean excludes(final String path) {
        return path.contains(MCP_SEGMENT);
    }
}
