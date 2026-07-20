package io.kestra.core.services;

import java.util.Map;

import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.Logs;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for parsing flows from their source.
 *
 * <p>
 * This service only parses and reports failures through typed exceptions — it never chooses how to react to
 * them. Callers own the reaction: the executor path converts failures into failed executions, the scheduler
 * path skips or degrades, and validation paths propagate.
 * </p>
 */
@Singleton
@Slf4j
public class FlowParsingService {
    private static final ObjectMapper YAML_MAPPER_NON_DEFAULT = JacksonMapper.ofYaml()
        .copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Parses the given abstract flow, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * This parses the flow as authored: plugin versions are injected (required to resolve versioned plugin
     * classes) but no runtime governance is applied — use {@link #parseForRuntime(FlowInterface)} for the
     * executor and scheduler paths.
     * </p>
     *
     * <p>
     * If {@code strictParsing} is {@code true}, the parsing will fail in the following cases:
     * </p>
     * <ul>
     * <li>The source contains duplicate properties.</li>
     * <li>The source contains unknown properties.</li>
     * </ul>
     *
     * @param flow the flow to be parsed
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a parsed {@link FlowWithSource}
     *
     * @throws FlowProcessingException if an error occurred while processing the flow
     */
    public FlowWithSource parse(final FlowInterface flow, final boolean strictParsing) throws FlowProcessingException {

        // Flow revisions created from older Kestra versions may not be linked to their original source.
        // In such cases, fall back to the generated source approach.
        String source = flow.sourceOrGenerateIfNull();

        if (source == null) {
            // This should never happen
            String error = "Cannot parse flow. Cause: flow has no defined source.";
            Logs.logExecution(flow, log, Level.ERROR, error);
            throw new IllegalArgumentException(error);
        }

        try {
            return parseFlow(
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getRevision(),
                flow.isDeleted(),
                source,
                strictParsing
            );
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses the given flow source.
     *
     * @param tenantId the Tenant ID.
     * @param source the flow source.
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a new {@link FlowWithSource}.
     *
     * @throws FlowProcessingException when parsing flow.
     */
    public FlowWithSource parse(@Nullable final String tenantId, final String source, final boolean strictParsing) throws FlowProcessingException {
        try {
            return parseFlow(tenantId, null, null, false, source, strictParsing);
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses the given abstract flow for the runtime paths (executor and scheduler), returning a parsed
     * {@link FlowWithSource}. Editions may override this method to apply governance to the flow about to run;
     * the open-source edition parses leniently without further processing.
     *
     * @param flow the flow to be parsed
     * @return a parsed {@link FlowWithSource}
     *
     * @throws FlowBlockedException if governance rejected the flow — it must not run
     * @throws FlowProcessingException if an error occurred while processing the flow
     */
    public FlowWithSource parseForRuntime(final FlowInterface flow) throws FlowProcessingException {
        return parse(flow, false);
    }

    /**
     * Converts the given abstract flow into a {@link FlowWithSource} without re-parsing it. Used by runtime
     * callers to degrade to the flow as stored when {@link #parseForRuntime(FlowInterface)} fails.
     */
    public static FlowWithSource toFlowWithSource(final FlowInterface flow) {
        if (flow instanceof FlowWithSource item) {
            return item;
        }

        if (flow instanceof Flow item) {
            return FlowWithSource.of(item, item.sourceOrGenerateIfNull());
        }

        // The block below should only be reached during testing for failure scenarios
        try {
            Flow parsed = YAML_MAPPER_NON_DEFAULT.readValue(flow.getSource(), Flow.class);
            return FlowWithSource.of(parsed, flow.getSource());
        } catch (JsonProcessingException e) {
            throw new KestraRuntimeException("Failed to read flow from source", e);
        }
    }

    /**
     * Injects plugin versions into the given flow map. No-op by default; may be overridden to resolve
     * versioned plugin classes when a stored flow is loaded for runtime.
     *
     * @param tenantId the Tenant ID.
     * @param namespace the namespace.
     * @param mapFlow the flow as a map.
     * @return the flow map, with plugin versions injected.
     */
    public Map<String, Object> injectPluginVersions(@Nullable final String tenantId,
        final String namespace,
        final Map<String, Object> mapFlow) throws FlowProcessingException {
        return mapFlow;
    }

    /**
     * Parses the given flow source into a {@link FlowWithSource}, injecting plugin versions.
     *
     * @param tenantId the tenant identifier.
     * @param namespace the namespace.
     * @param revision the flow revision.
     * @param source the flow source.
     * @return a new {@link FlowWithSource}.
     *
     * @throws ConstraintViolationException when parsing flow.
     */
    private FlowWithSource parseFlow(@Nullable final String tenantId,
        @Nullable String namespace,
        @Nullable Integer revision,
        final boolean isDeleted,
        final String source,
        final boolean strictParsing) throws ConstraintViolationException, JsonProcessingException, FlowProcessingException {
        Map<String, Object> mapFlow = readFlowAsMap(tenantId, namespace, source);
        return parseFlowFromMap(mapFlow, tenantId, revision, isDeleted, source, strictParsing);
    }

    /**
     * Reads the given flow source into its map representation, resolving the namespace from the source when absent
     * and injecting plugin versions. Protected so editions can parse once and transform the map before it is
     * deserialized through {@link #parseFlowFromMap}.
     *
     * @throws JsonProcessingException when the source is not valid YAML.
     * @throws FlowProcessingException when injecting plugin versions fails.
     */
    protected Map<String, Object> readFlowAsMap(@Nullable final String tenantId,
        @Nullable final String namespace,
        final String source) throws JsonProcessingException, FlowProcessingException {
        Map<String, Object> mapFlow = YAML_MAPPER.readValue(source, JacksonMapper.MAP_TYPE_REFERENCE);
        return injectPluginVersions(tenantId, namespace == null ? (String) mapFlow.get("namespace") : namespace, mapFlow);
    }

    /**
     * Deserializes a flow map representation — as produced by {@link #readFlowAsMap} — into a {@link FlowWithSource}.
     *
     * @throws ConstraintViolationException when the map does not describe a valid flow.
     */
    protected FlowWithSource parseFlowFromMap(final Map<String, Object> mapFlow,
        @Nullable final String tenantId,
        @Nullable final Integer revision,
        final boolean isDeleted,
        final String source,
        final boolean strictParsing) throws ConstraintViolationException {
        FlowWithSource parsed = YamlParser.parse(mapFlow, FlowWithSource.class, strictParsing);

        // revision, tenants, and deleted are not in the 'source', so we copy them manually
        return parsed.toBuilder()
            .tenantId(tenantId)
            .revision(revision == null ? (Integer) mapFlow.get("revision") : revision)
            .deleted(isDeleted)
            .source(source)
            .build();
    }
}
