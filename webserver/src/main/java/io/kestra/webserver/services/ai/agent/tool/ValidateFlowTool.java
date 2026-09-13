package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

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
        value = "Validate Kestra flow YAML without saving or executing anything, reporting validation errors, warnings and deprecations. Read-only; always use this to check flow YAML before proposing it. "
            + "Returns an object { valid, errors, warnings, deprecatedPaths, infos } where `valid` is true only when `errors` is empty; each of the other fields is an array of messages."
    )
    public Result validateFlow(
        @P(name = "flowYaml", value = "The full flow YAML source to validate") String flowYaml,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        List<ValidateConstraintViolation> violations = flowService.validate(tenant, List.of(new FlowSource(null, flowYaml)));

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> deprecatedPaths = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        for (ValidateConstraintViolation violation : violations) {
            if (violation.getConstraints() != null && !violation.getConstraints().isBlank()) {
                errors.add(violation.getConstraints());
            }
            if (!ListUtils.isEmpty(violation.getWarnings())) {
                warnings.addAll(violation.getWarnings());
            }
            if (!ListUtils.isEmpty(violation.getDeprecationPaths())) {
                deprecatedPaths.addAll(violation.getDeprecationPaths());
            }
            if (!ListUtils.isEmpty(violation.getInfos())) {
                infos.addAll(violation.getInfos());
            }
        }

        return new Result(errors.isEmpty(), errors, warnings, deprecatedPaths, infos);
    }

    /**
     * The outcome of validating flow YAML.
     *
     * @param valid true only when there are no errors
     * @param errors constraint-violation messages, empty when valid
     * @param warnings non-blocking warnings
     * @param deprecatedPaths paths that use deprecated properties
     * @param infos informational messages
     */
    public record Result(boolean valid, List<String> errors, List<String> warnings, List<String> deprecatedPaths, List<String> infos) {
    }
}
