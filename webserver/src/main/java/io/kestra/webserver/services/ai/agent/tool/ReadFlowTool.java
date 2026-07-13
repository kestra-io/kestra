package io.kestra.webserver.services.ai.agent.tool;

import java.util.Optional;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

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

    @Tool(name = "read-flow", value = "Read the YAML source of a Kestra flow. Read-only; use this to inspect a flow's definition before explaining, diagnosing or proposing changes to it.")
    public String readFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "revision", value = "Optional flow revision to read; omit to read the latest revision", required = false) Integer revision,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        FlowWithSource flow = flowRepository.findByIdWithSource(tenant, namespace, flowId, Optional.ofNullable(revision), false)
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Flow not found: '" + namespace + "." + flowId + "'"
                        + (revision == null ? "" : " (revision " + revision + ")")
                )
            );

        return flow.getSource();
    }
}
