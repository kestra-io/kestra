package io.kestra.webserver.models.flows;

import java.util.List;

public record SourceSearchReplacePreviewResponse(
    int totalMatches,
    int totalFlows,
    int editableFlowCount,
    List<FlowMatches> flows
) {
    public record FlowMatches(String namespace, String id, boolean editable, List<Match> matches) {
    }

    public record Match(int line, String before, String after) {
    }
}
