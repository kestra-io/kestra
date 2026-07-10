package io.kestra.core.services;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.Logs;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for parsing flows from their source.
 */
@Singleton
@Slf4j
public class FlowParsingService {
    private static final ObjectMapper YAML_MAPPER_NON_DEFAULT = JacksonMapper.ofYaml()
        .copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public FlowParsingService(RunContextLoggerFactory runContextLoggerFactory) {
        this.runContextLoggerFactory = runContextLoggerFactory;
    }

    /**
     * Parses the given abstract flow, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If an exception occurs during parsing, the original flow is returned unchanged, and the exception is logged
     * for the passed {@code execution}
     * </p>
     *
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails
     */
    public FlowWithSource parseForExecution(FlowInterface flow, Execution execution) {
        try {
            return this.parse(flow, false);
        } catch (Exception e) {
            var logger = runContextLoggerFactory.create(execution);
            logger.emitLogs(RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)));
            return readOrThrow(flow);
        }
    }

    /**
     * Parses the given abstract flow for validation before persistence. Plugin versions are injected (required
     * to resolve versioned plugin classes) but no execution-time policy enforcement is applied.
     *
     * @param flow the flow to be parsed
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a parsed {@link FlowWithSource}
     *
     * @throws FlowProcessingException if an error occurred while processing the flow
     */
    public FlowWithSource parseForValidation(final FlowInterface flow, final boolean strictParsing) throws FlowProcessingException {
        return parse(flow, strictParsing);
    }

    /**
     * Parses the given abstract flow for trigger evaluation, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If an exception occurs during parsing, the original flow is returned unchanged, and the exception is logged.
     * </p>
     *
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails
     */
    public FlowWithSource parseForTrigger(FlowInterface flow, Logger logger) {
        try {
            return this.parse(flow, false);
        } catch (Exception e) {
            logger.warn(
                "Can't parse flow on tenant {}, namespace '{}', flow '{}' with errors '{}'",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage(),
                e
            );
            return readOrThrow(flow);
        }
    }

    private static FlowWithSource readOrThrow(final FlowInterface flow) {
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
     * Parses the given abstract flow, returning a parsed {@link FlowWithSource}.
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
     * Parses the given abstract flow, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If the provided flow already represents a concrete {@link FlowWithSource}, it is returned as is.
     * <p/>
     *
     * <p>
     * If {@code safe} is set to {@code true} and the given flow cannot be parsed,
     * this method returns a {@link FlowWithException} instead of throwing an error.
     * <p/>
     *
     * @param flow the flow to be parsed
     * @param safe whether parsing errors should be handled gracefully
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails and {@code safe} is {@code true}
     *
     * @throws FlowProcessingException if an error occurred while processing the flow and {@code safe} is {@code false}.
     */
    public FlowWithSource parseSafely(final FlowInterface flow, final boolean safe) throws FlowProcessingException {
        if (flow instanceof FlowWithSource flowWithSource) {
            // shortcut - if the flow is already fully parsed return it immediately.
            return flowWithSource;
        }

        FlowWithSource result;

        try {
            String source = flow.getSource();
            if (source == null) {
                source = YAML_MAPPER.writeValueAsString(flow);
            }

            result = parseFlow(flow.getTenantId(), flow.getNamespace(), flow.getRevision(), flow.isDeleted(), source, false);
        } catch (Exception e) {
            if (safe) {
                Logs.logExecution(flow, log, Level.ERROR, "Failed to read flow.", e);
                result = FlowWithException.from(flow, e);

                // deleted is not part of the original 'source'
                result = result.toBuilder().deleted(flow.isDeleted()).build();
            } else {
                throw new FlowProcessingException(e);
            }
        }
        return result;
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
     * Parses the given flow source.
     *
     * @param tenantId the Tenant ID.
     * @param source the flow source.
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a new {@link FlowWithSource}.
     *
     * @throws FlowProcessingException when parsing flow.
     */
    public FlowWithSource parseFlow(@Nullable final String tenantId, final String source, final boolean strictParsing) throws FlowProcessingException {
        try {
            return parseFlow(tenantId, null, null, false, source, strictParsing);
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses the given flow source for validation before persistence. Plugin versions are injected (required
     * to resolve versioned plugin classes) but no execution-time policy enforcement is applied.
     *
     * @param tenantId the Tenant ID.
     * @param source the flow source.
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a new {@link FlowWithSource}.
     *
     * @throws FlowProcessingException when parsing flow.
     */
    public FlowWithSource parseForValidation(@Nullable final String tenantId, final String source, final boolean strictParsing) throws FlowProcessingException {
        return parseFlow(tenantId, source, strictParsing);
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
