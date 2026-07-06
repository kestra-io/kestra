package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.converters.QueryFilterFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * A {@link ToolExecutor} for a tool whose {@code List<QueryFilter>} parameter was expanded per-field
 * by {@link AiToolSpecifications}. It reads the per-field {@code {operator, value}} arguments the
 * model returns, reassembles them into a single {@code List<QueryFilter>} (constructing
 * {@link QueryFilter} directly so its Jackson {@code @JsonCreator} validation runs — no JSON
 * round-trip through langchain4j's coercion), validates each against the resource's
 * {@code supportedField()} / {@code supportedOp()}, then invokes the target method.
 */
public final class QueryFilterToolExecutor implements ToolExecutor {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final Object toolInstance;
    private final Method method;
    private final Parameter[] parameters;
    private final int filterParamIndex;
    private final QueryFilter.Resource resource;

    public QueryFilterToolExecutor(final Object toolInstance, final Method method) {
        this.toolInstance = toolInstance;
        this.method = method;
        this.parameters = method.getParameters();
        this.filterParamIndex = findFilterParamIndex(this.parameters, method);
        this.resource = parameters[filterParamIndex].getAnnotation(QueryFilterFormat.class).value();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        Map<String, Object> args = parseArguments(request.arguments());
        List<QueryFilter> filters = reassembleFilters(args);
        return invoke(bindArguments(filters, args));
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

    private Object[] bindArguments(final List<QueryFilter> filters, final Map<String, Object> args) {
        Object[] bound = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            bound[i] = bindArgument(i, filters, args);
        }
        return bound;
    }

    private Object bindArgument(final int index, final List<QueryFilter> filters, final Map<String, Object> args) {
        if (index == filterParamIndex) {
            return filters;
        }
        Object raw = args.get(AiToolSpecifications.parameterName(parameters[index]));
        if (raw == null) {
            return null;
        }
        return MAPPER.convertValue(raw, MAPPER.getTypeFactory().constructType(parameters[index].getParameterizedType()));
    }

    private String invoke(final Object[] bound) {
        try {
            Object result = method.invoke(toolInstance, bound);
            return result == null ? "" : result.toString();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to invoke tool method " + method, e);
        }
    }

    private List<QueryFilter> reassembleFilters(final Map<String, Object> args) {
        List<QueryFilter> filters = new ArrayList<>();
        for (QueryFilter.Field field : resource.supportedField()) {
            if (!AiToolSpecifications.isExposedFilterField(field)) {
                continue;   // not exposed in the schema (e.g. TIME_RANGE) — ignore if the model sent it
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
                throw new IllegalArgumentException("Unknown operator '" + operatorName + "' for field '" + field.name() + "'");
            }
            if (!field.supportedOp().contains(op)) {
                throw new IllegalArgumentException(
                    "Operator '" + op + "' is not supported for field '" + field.name() + "'. Valid: " + field.supportedOp());
            }
            // leaf shape: field + operation present, no logical/children (see QueryFilter's @JsonCreator).
            filters.add(new QueryFilter(field, op, entry.get("value"), null, null));
        }
        return filters;
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
}
