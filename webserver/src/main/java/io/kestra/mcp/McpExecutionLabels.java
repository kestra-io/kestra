package io.kestra.mcp;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.Label;

/**
 * The system labels every execution started through an MCP server carries, whichever tool started it.
 */
public final class McpExecutionLabels {
    private McpExecutionLabels() {
    }

    public static List<Label> of(KestraMcpTransportContext context) {
        List<Label> labels = new ArrayList<>(4);
        labels.add(new Label(Label.FROM, Label.FromLabel.MCP.value));
        labels.add(new Label(Label.MCP_SERVER_ID, context.getServerId()));
        labels.add(new Label(Label.MCP_SESSION_ID, context.getSessionId()));
        if (context.getUsername() != null) {
            labels.add(new Label(Label.USERNAME, context.getUsername()));
        }
        return labels;
    }
}
