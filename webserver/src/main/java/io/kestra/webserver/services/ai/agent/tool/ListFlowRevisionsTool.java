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
        value = "List the revisions of a Kestra flow, newest first (at most " + MAX_RESULTS + " returned). Read-only; use this to see how often and when a flow changed, "
            + "then read_flow with a `revision` to get the source of a given revision, for example to compare two of them. "
            + "Returns an object { revisions, total } where `revisions` is an array of { revision, updated } and `updated` is an ISO-8601 date-time or null. "
            + "`total` is the number of all revisions, which can exceed the length of `revisions`."
    )
    public Result listFlowRevisions(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        final AgentCallContext.Context context) {
        List<FlowWithSource> revisions = flowRepository.findRevisions(context.tenant(), namespace, flowId, false);
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("Flow not found: '%s.%s'".formatted(namespace, flowId));
        }

        return new Result(
            revisions.stream()
                .sorted(Comparator.comparing(FlowWithSource::getRevision).reversed())
                .limit(MAX_RESULTS)
                .map(flow -> new Revision(flow.getRevision(), flow.getUpdated()))
                .toList(),
            revisions.size()
        );
    }

    public record Result(List<Revision> revisions, long total) {
    }

    public record Revision(Integer revision, Instant updated) {
    }
}
