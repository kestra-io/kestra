package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.converters.QueryFilterFormat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * Derives a {@link ToolSpecification} from a method exactly like
 * {@link ToolSpecifications#toolSpecificationFrom(Method)}, except that any parameter annotated with
 * {@link QueryFilterFormat} is <em>expanded</em> in the input schema: instead of a generic
 * {@code List<QueryFilter>} array, the model sees one property per filterable field of the resource,
 * each a {@code {operator, value}} object whose {@code operator} is a field-scoped enum.
 *
 * <p>
 * Implementation: reuse langchain4j's derivation for the whole method (name, description,
 * metadata, and every non-filter parameter), then surgically replace the filter parameter's property
 * with the expanded per-field properties. This avoids reimplementing the {@code @Internal}
 * {@code JsonSchemaElementUtils} while giving full control over the schema. (langchain4j already
 * omits managed parameters, e.g. the injected {@code AgentCallContext.Context}, from the schema,
 * so they never reach the model.)
 * </p>
 */
public final class AiToolSpecifications {

    /**
     * Appended to a tool's description when it has query-filter parameters, so the model knows how to
     * shape values — in particular the date formats. Mirrors the flow-as-MCP-tool convention
     * ({@code format: date-time} / {@code duration}, i.e. ISO-8601).
     */
    private static final String FILTER_VALUE_GUIDANCE = "Filter values: each field takes an { operator, value }. For date/time fields (e.g. START_DATE, "
        + "END_DATE) the value must be either an ISO-8601 date-time (e.g. 2026-07-05T14:30:00Z) or an "
        + "ISO-8601 duration relative to now (e.g. PT5M, PT1H, P1D).";

    private AiToolSpecifications() {
    }

    static boolean isExposedFilterField(final QueryFilter.Field field) {
        return field != QueryFilter.Field.TIME_RANGE;
    }

    /**
     * @param method the {@code @Tool}-annotated method
     */
    public static ToolSpecification toolSpecificationFrom(final Method method) {
        ToolSpecification base = ToolSpecifications.toolSpecificationFrom(method);

        List<Parameter> filterParams = Arrays.stream(method.getParameters())
            .filter(p -> p.isAnnotationPresent(QueryFilterFormat.class))
            .toList();
        if (filterParams.isEmpty() || !(base.parameters() instanceof JsonObjectSchema params)) {
            return base;
        }

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>(params.properties());
        List<String> required = new ArrayList<>(params.required() == null ? List.of() : params.required());

        for (Parameter filterParam : filterParams) {
            String name = parameterName(filterParam);
            properties.remove(name); // drop the generic List<QueryFilter> property
            required.remove(name);

            QueryFilter.Resource resource = filterParam.getAnnotation(QueryFilterFormat.class).value();
            for (QueryFilter.Field field : resource.supportedField()) {
                if (!isExposedFilterField(field)) {
                    continue;
                }
                properties.put(field.name(), fieldFilterSchema(field)); // optional per-field filter
            }
        }

        JsonObjectSchema reshaped = JsonObjectSchema.builder()
            .addProperties(properties)
            .required(required)
            .definitions(params.definitions())
            .build();

        return base.toBuilder()
            .parameters(reshaped)
            .description(withFilterValueGuidance(base.description()))
            .build();
    }

    private static JsonObjectSchema fieldFilterSchema(final QueryFilter.Field field) {
        List<String> operators = field.supportedOp().stream().map(Enum::name).toList();
        return JsonObjectSchema.builder()
            .description("Filter by " + field.name())
            .addProperty("operator", JsonEnumSchema.builder().enumValues(operators).build())
            .addProperty("value", new JsonStringSchema())
            .required(List.of("operator", "value"))
            .build();
    }

    private static String withFilterValueGuidance(final String description) {
        if (description == null || description.isBlank()) {
            return FILTER_VALUE_GUIDANCE;
        }
        return description + "\n\n" + FILTER_VALUE_GUIDANCE;
    }

    static String parameterName(final Parameter parameter) {
        P p = parameter.getAnnotation(P.class);
        if (p != null && p.name() != null && !p.name().isBlank()) {
            return p.name();
        }
        return parameter.getName();
    }
}
