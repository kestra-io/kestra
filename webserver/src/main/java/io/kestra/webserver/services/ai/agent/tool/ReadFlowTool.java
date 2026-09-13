package io.kestra.webserver.services.ai.agent.tool;

import java.util.Optional;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool returning the YAML source of a flow, optionally at a specific revision.
 */
@Singleton
public class ReadFlowTool implements AiPlatformTool {
    private final FlowRepositoryInterface flowRepository;

    @Inject
    public ReadFlowTool(final FlowRepositoryInterface flowRepository) {
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

    @Tool(
        name = "read-flow", value = "Read the source of a Kestra flow. Read-only; use this to inspect a flow's definition before explaining, diagnosing or proposing changes to it. "
            + "Returns an object { namespace, id, revision, source } where `source` is the flow's YAML and `revision` is the revision that was read."
    )
    public Result readFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "revision", value = "Optional flow revision to read; omit to read the latest revision", required = false) Integer revision,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        FlowWithSource flow = flowRepository.findByIdWithSource(tenant, namespace, flowId, Optional.ofNullable(revision), false)
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Flow not found: '%s.%s'".formatted(namespace, flowId)
                        + (revision == null ? "" : " (revision %s)".formatted(revision))
                )
            );

        return new Result(flow.getNamespace(), flow.getId(), flow.getRevision(), flow.getSource());
    }

    /**
     * The source of a single flow.
     *
     * @param namespace the flow's namespace
     * @param id the flow's id
     * @param revision the revision that was read
     * @param source the flow's YAML source
     */
    public record Result(String namespace, String id, Integer revision, String source) {
    }
}
