package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool validating flow YAML without saving it, reporting errors, warnings,
 * deprecated paths and infos.
 */
@Singleton
public class ValidateFlowTool implements AiPlatformTool {
    private final FlowService flowService;

    @Inject
    public ValidateFlowTool(final FlowService flowService) {
        this.flowService = flowService;
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
        name = "validate-flow",
        value = "Validate Kestra flow YAML without saving or executing anything, reporting validation errors, warnings and deprecations. Read-only; always use this to check flow YAML before proposing it."
    )
    public String validateFlow(
        @P(name = "flowYaml", value = "The full flow YAML source to validate") String flowYaml,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        List<ValidateConstraintViolation> violations = flowService.validate(tenant, List.of(new FlowSource(null, flowYaml)));

        boolean hasErrors = false;
        List<String> lines = new ArrayList<>();
        for (ValidateConstraintViolation violation : violations) {
            if (violation.getConstraints() != null && !violation.getConstraints().isBlank()) {
                hasErrors = true;
                lines.add("Errors: " + violation.getConstraints());
            }
            if (!ListUtils.isEmpty(violation.getWarnings())) {
                lines.add("Warnings: " + String.join("; ", violation.getWarnings()));
            }
            if (!ListUtils.isEmpty(violation.getDeprecationPaths())) {
                lines.add("Deprecated paths: " + String.join(", ", violation.getDeprecationPaths()));
            }
            if (!ListUtils.isEmpty(violation.getInfos())) {
                lines.add("Infos: " + String.join("; ", violation.getInfos()));
            }
        }

        if (!hasErrors) {
            lines.add(0, "The flow is valid.");
        }
        return String.join("\n", lines);
    }
}
