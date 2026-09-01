package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DisplayExpressionRenderer;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.SecureVariableRendererFactory;
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
import jakarta.validation.constraints.NotBlank;
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
    private final SecureVariableRendererFactory secureVariableRendererFactory;

    @Inject
    public ExpressionController(
        TenantService tenantService,
        ExecutionRepositoryInterface executionRepository,
        FlowRepositoryInterface flowRepository,
        FlowParsingService flowParsingService,
        RunContextFactory runContextFactory,
        DisplayExpressionRenderer displayExpressionRenderer,
        SecureVariableRendererFactory secureVariableRendererFactory) {
        this.tenantService = tenantService;
        this.executionRepository = executionRepository;
        this.flowRepository = flowRepository;
        this.flowParsingService = flowParsingService;
        this.runContextFactory = runContextFactory;
        this.displayExpressionRenderer = displayExpressionRenderer;
        this.secureVariableRendererFactory = secureVariableRendererFactory;
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

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/eval", consumes = MediaType.APPLICATION_JSON)
    @Operation(
        tags = { "Expressions" },
        summary = "Evaluate a Pebble expression against a flow or execution context",
        description = "Evaluates a single Pebble expression with the full rendering engine, so it returns exactly what " +
            "the expression would resolve to at runtime: filters, operators and functions with side effects such as " +
            "kv() are all honoured, and an expression that cannot be resolved returns an error message rather than the " +
            "raw text (that is what /render is for). secret() is masked. Provide an executionId to evaluate against an " +
            "execution context, or a flow source (an unsaved editor draft) or a namespace/flowId pair to evaluate " +
            "against a flow context; otherwise only globals are available."
    )
    public EvaluatedExpression evalExpression(@Valid @Body EvalExpressionRequest request) throws FlowProcessingException {
        Map<String, Object> variables = variablesFor(request);

        try {
            // The secure renderer masks secret(); everything else resolves as it would during an execution,
            // matching POST /executions/{id}/actions/eval.
            return new EvaluatedExpression(secureVariableRendererFactory.createOrGet().render(request.expression(), variables), null);
        } catch (IllegalVariableEvaluationException e) {
            return new EvaluatedExpression(null, e.getMessage());
        }
    }

    private Map<String, Object> variablesFor(ExpressionContext request) throws FlowProcessingException {
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

    /**
     * The context an expression is resolved against, shared by every route on this controller so they all
     * follow the same resolution priority — and so a subclass can authorize that context in one place.
     */
    public interface ExpressionContext {
        @Nullable
        String executionId();

        @Nullable
        String namespace();

        @Nullable
        String flowId();

        @Nullable
        String flow();
    }

    public record RenderExpressionRequest(
        @NotEmpty @Size(max = 500) @Schema(description = "The raw Pebble expressions to render") List<String> expressions,
        @Nullable @Schema(description = "Resolve against this execution's context") String executionId,
        @Nullable @Schema(description = "Resolve against this flow's context (with flowId)") String namespace,
        @Nullable @Schema(description = "Resolve against this flow's context (with namespace)") String flowId,
        @Nullable @Schema(description = "Resolve against this flow source's context (YAML)") String flow) implements ExpressionContext {
    }

    public record RenderedExpressions(
        @Schema(description = "Rendered values keyed by their raw expression") Map<String, String> rendered) {
    }

    public record EvalExpressionRequest(
        @NotBlank @Size(max = 8192) @Schema(description = "The raw Pebble expression to evaluate") String expression,
        @Nullable @Schema(description = "Evaluate against this execution's context") String executionId,
        @Nullable @Schema(description = "Evaluate against this flow's context (with flowId)") String namespace,
        @Nullable @Schema(description = "Evaluate against this flow's context (with namespace)") String flowId,
        @Nullable @Schema(description = "Evaluate against this flow source's context (YAML)") String flow) implements ExpressionContext {
    }

    public record EvaluatedExpression(
        @Nullable @Schema(description = "The evaluated value, null when the expression could not be evaluated") String result,
        @Nullable @Schema(description = "Why the expression could not be evaluated, null on success") String error) {
    }
}
