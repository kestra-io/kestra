package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DisplayExpressionRenderer;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.tenant.TenantService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Controller("/api/v1/{tenant}/expressions")
public class ExpressionController {

    private final TenantService tenantService;
    private final ExecutionRepositoryInterface executionRepository;
    private final FlowRepositoryInterface flowRepository;
    private final FlowParsingService flowParsingService;
    private final RunContextFactory runContextFactory;
    private final DisplayExpressionRenderer displayExpressionRenderer;

    @Inject
    public ExpressionController(
        TenantService tenantService,
        ExecutionRepositoryInterface executionRepository,
        FlowRepositoryInterface flowRepository,
        FlowParsingService flowParsingService,
        RunContextFactory runContextFactory,
        DisplayExpressionRenderer displayExpressionRenderer) {
        this.tenantService = tenantService;
        this.executionRepository = executionRepository;
        this.flowRepository = flowRepository;
        this.flowParsingService = flowParsingService;
        this.runContextFactory = runContextFactory;
        this.displayExpressionRenderer = displayExpressionRenderer;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/render", consumes = MediaType.APPLICATION_JSON)
    @Operation(
        tags = { "Expressions" },
        summary = "Render Pebble expressions for display",
        description = "Renders a list of Pebble expressions for display purposes only, using a restricted engine: " +
            "secret() is masked as [secret: KEY], env() is kept raw, only a safe allowlist of pure functions is " +
            "invoked, and anything else is kept raw. Resolution is all-or-nothing per expression: an expression that " +
            "references anything unresolvable is returned unchanged. Provide an executionId to resolve against an " +
            "execution context, or a flow source to resolve against a flow context; otherwise only globals are available."
    )
    public RenderedExpressions renderExpressions(@Valid @Body RenderExpressionRequest request) throws FlowProcessingException {
        Map<String, Object> variables = variablesFor(request);
        return new RenderedExpressions(displayExpressionRenderer.render(request.expressions(), variables));
    }

    private Map<String, Object> variablesFor(RenderExpressionRequest request) throws FlowProcessingException {
        if (request.executionId() != null) {
            Execution execution = executionRepository
                .findById(tenantService.resolveTenant(), request.executionId())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unable to find execution '" + request.executionId() + "'"));
            Flow flow = flowRepository.findByExecution(execution);

            return runContextFactory.of(flow, execution, false).getVariables();
        }

        // A flow source (e.g. an unsaved editor draft) takes priority over the persisted flow.
        if (request.flow() != null) {
            FlowWithSource flow = flowParsingService.parse(tenantService.resolveTenant(), request.flow(), false);

            return flowVariables(flow);
        }

        if (request.namespace() != null && request.flowId() != null) {
            Flow flow = flowRepository
                .findById(tenantService.resolveTenant(), request.namespace(), request.flowId(), Optional.empty())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Unable to find flow '" + request.namespace() + "." + request.flowId() + "'"));

            return flowVariables(flow);
        }

        return Map.of();
    }

    private Map<String, Object> flowVariables(Flow flow) {
        // RunVariables exposes flow.* and flow-level vars.* even without an execution.
        return runContextFactory.of(flow, Map.of()).getVariables();
    }

    public record RenderExpressionRequest(
        @NotEmpty @Size(max = 500) @Schema(description = "The raw Pebble expressions to render") List<String> expressions,
        @Nullable @Schema(description = "Resolve against this execution's context") String executionId,
        @Nullable @Schema(description = "Resolve against this flow's context (with flowId)") String namespace,
        @Nullable @Schema(description = "Resolve against this flow's context (with namespace)") String flowId,
        @Nullable @Schema(description = "Resolve against this flow source's context (YAML)") String flow) {
    }

    public record RenderedExpressions(
        @Schema(description = "Rendered values keyed by their raw expression") Map<String, String> rendered) {
    }
}
