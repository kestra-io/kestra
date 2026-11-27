package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the revisions of a flow, newest first, without their sources.
 */
@Singleton
public class ListFlowRevisionsTool implements McpPlatformTool {
    private static final int MAX_RESULTS = 50;

    private final FlowRepositoryInterface flowRepository;

    @Inject
    public ListFlowRevisionsTool(final FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "list_flow_revisions",
        value = "List the revisions of a Kestra flow, newest first (" + MAX_RESULTS + " per page). Read-only; use this to see how often and when a flow changed, "
            + "then read_flow with a `revision` to get the source of a given revision, for example to compare two of them. "
            + "Returns an object { revisions, total, page } where `revisions` is an array of { revision, updated } and `updated` is an ISO-8601 date-time or null. "
            + "`total` is the number of all revisions; when it exceeds what was read so far, request the next `page`."
    )
    public Result listFlowRevisions(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "page", value = "The 1-based page to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        int pageNumber = ToolPages.pageNumber(page);
        List<FlowWithSource> revisions = flowRepository.findRevisions(context.tenant(), namespace, flowId, false);
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("Flow not found: '%s.%s'".formatted(namespace, flowId));
        }

        List<FlowWithSource> newestFirst = revisions.stream()
            .sorted(Comparator.comparing(FlowWithSource::getRevision).reversed())
            .toList();

        return new Result(
            ToolPages.slice(newestFirst, pageNumber, MAX_RESULTS).stream()
                .map(flow -> new Revision(flow.getRevision(), flow.getUpdated()))
                .toList(),
            revisions.size(),
            pageNumber
        );
    }

    public record Result(List<Revision> revisions, long total, int page) {
    }

    public record Revision(Integer revision, Instant updated) {
    }
}
