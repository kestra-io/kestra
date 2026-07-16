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
 * It also hides any {@link TenantId}-annotated parameter from the schema unless
 * {@code exposeTenantId} is set: the parameter stays in the Java signature (bound to {@code null}
 * when absent) but the model only sees it in editions that offer tenant targeting.
 * </p>
 *
 * <p>
 * Implementation: reuse langchain4j's derivation for the whole method (name, description,
 * metadata, and every non-filter parameter), then surgically replace the filter parameter's property
 * with the expanded per-field properties and drop hidden parameters. This avoids reimplementing the
 * {@code @Internal} {@code JsonSchemaElementUtils} while giving full control over the schema.
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
     * @param exposeTenantId whether {@link TenantId} parameters are shown to the model or hidden
     */
    public static ToolSpecification toolSpecificationFrom(final Method method, final boolean exposeTenantId) {
        ToolSpecification base = ToolSpecifications.toolSpecificationFrom(method);

        List<Parameter> filterParams = Arrays.stream(method.getParameters())
            .filter(p -> p.isAnnotationPresent(QueryFilterFormat.class))
            .toList();
        List<Parameter> hiddenParams = exposeTenantId ? List.of()
            : Arrays.stream(method.getParameters())
                .filter(p -> p.isAnnotationPresent(TenantId.class))
                .toList();
        if ((filterParams.isEmpty() && hiddenParams.isEmpty()) || !(base.parameters() instanceof JsonObjectSchema params)) {
            return base;
        }

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>(params.properties());
        List<String> required = new ArrayList<>(params.required() == null ? List.of() : params.required());

        // hide tenant-targeting parameters from the model; they stay in the signature, bound to null
        for (Parameter hiddenParam : hiddenParams) {
            String name = parameterName(hiddenParam);
            properties.remove(name);
            required.remove(name);
        }

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

        ToolSpecification.Builder builder = base.toBuilder().parameters(reshaped);
        if (!filterParams.isEmpty()) {
            builder.description(withFilterValueGuidance(base.description()));
        }
        return builder.build();
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
