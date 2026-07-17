package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.converters.QueryFilterFormat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * A {@link ToolExecutor} for a tool whose {@code List<QueryFilter>} parameter was expanded per-field
 * by {@link AiToolSpecifications}. It reads the per-field {@code {operator, value}} arguments the
 * model returns, reassembles and validates them into a single {@code List<QueryFilter>} (constructing
 * {@link QueryFilter} directly so field/operator validity is checked with clear errors), then rewrites
 * the arguments to place that list under the real filter parameter and hands off to a
 * {@link DefaultToolExecutor}.
 *
 * <p>
 * Everything after reassembly — binding the remaining parameters (including the managed
 * {@code AgentCallContext.Context} argument injected from the {@link InvocationContext}), invoking
 * the method, serializing the result and propagating tool exceptions — is done by the delegate, so
 * this executor only owns the per-field → {@code List<QueryFilter>} translation.
 * </p>
 */
public final class QueryFilterToolExecutor implements ToolExecutor {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final QueryFilter.Resource resource;
    private final String filterParameterName;
    private final DefaultToolExecutor delegate;

    public QueryFilterToolExecutor(final Object toolInstance, final Method method) {
        Parameter[] parameters = method.getParameters();
        Parameter filterParameter = parameters[findFilterParamIndex(parameters, method)];
        this.resource = filterParameter.getAnnotation(QueryFilterFormat.class).value();
        this.filterParameterName = AiToolSpecifications.parameterName(filterParameter);
        // Reuse langchain4j's executor for the actual invocation; propagate exceptions so the
        // orchestrator can turn a tool failure into a recoverable error result (see ToolCatalog).
        this.delegate = DefaultToolExecutor.builder()
            .object(toolInstance)
            .originalMethod(method)
            .methodToInvoke(method)
            .propagateToolExecutionExceptions(true)
            .build();
    }

    @Override
    public ToolExecutionResult executeWithContext(final ToolExecutionRequest request, final InvocationContext context) {
        Map<String, Object> args = parseArguments(request.arguments());
        List<QueryFilter> filters = reassembleFilters(resource, args);
        // Hand the delegate the minimal { field, operation, value } shape rather than serialized
        // QueryFilter objects: those carry derived getters (e.g. `node`) that the delegate's strict
        // JSON codec rejects. The delegate re-materializes each leaf through QueryFilter's @JsonCreator.
        args.put(filterParameterName, filters.stream().map(QueryFilterToolExecutor::toArgument).toList());

        ToolExecutionRequest rewritten = ToolExecutionRequest.builder()
            .id(request.id())
            .name(request.name())
            .arguments(writeArguments(args))
            .build();
        return delegate.executeWithContext(rewritten, context);
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        return executeWithContext(request, InvocationContext.builder().chatMemoryId(memoryId).build()).resultText();
    }

    /** Locate the single {@code @QueryFilterFormat} parameter; fail if the method declares none. */
    private static int findFilterParamIndex(final Parameter[] parameters, final Method method) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(QueryFilterFormat.class)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No @QueryFilterFormat parameter on " + method);
    }

    private static List<QueryFilter> reassembleFilters(final QueryFilter.Resource resource, final Map<String, Object> args) {
        List<QueryFilter> filters = new ArrayList<>();
        for (QueryFilter.Field field : resource.supportedField()) {
            if (!AiToolSpecifications.isExposedFilterField(field)) {
                continue; // not exposed in the schema (e.g. TIME_RANGE) — ignore if the model sent it
            }
            Object raw = args.remove(field.name());
            if (!(raw instanceof Map<?, ?> entry)) {
                continue;
            }
            String operatorName = String.valueOf(entry.get("operator"));
            QueryFilter.Op op;
            try {
                op = QueryFilter.Op.valueOf(operatorName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown operator '%s' for field '%s'".formatted(operatorName, field.name()));
            }
            if (!field.supportedOp().contains(op)) {
                throw new IllegalArgumentException(
                    "Operator '%s' is not supported for field '%s'. Valid: %s".formatted(op, field.name(), field.supportedOp())
                );
            }
            // leaf shape: field + operation present, no logical/children (see QueryFilter's @JsonCreator).
            filters.add(new QueryFilter(field, op, entry.get("value"), null, null));
        }
        return filters;
    }

    /** The minimal creator-recognized shape of a leaf filter — enums serialize via their @JsonValue. */
    private static Map<String, Object> toArgument(final QueryFilter filter) {
        Map<String, Object> argument = new LinkedHashMap<>();
        argument.put("field", filter.field());
        argument.put("operation", filter.operation());
        argument.put("value", filter.value());
        return argument;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(final String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(MAPPER.readValue(json, Map.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid tool arguments JSON: " + e.getMessage(), e);
        }
    }

    private static String writeArguments(final Map<String, Object> args) {
        try {
            return MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize tool arguments: " + e.getMessage(), e);
        }
    }
}
