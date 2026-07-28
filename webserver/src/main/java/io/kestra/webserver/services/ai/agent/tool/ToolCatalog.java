package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The single source of truth for what the agent can do, and the single dispatch point both front
 * doors converge on. Each {@link AiPlatformTool} is reflected once — name/description/input schema
 * derived from its {@code @Tool}-annotated method — and merged with the docs MCP tools. AgentMode
 * profiles select a subset of these specs per turn; every call is routed back through
 * {@link #dispatch} for execution.
 *
 * <p>
 * A tool runs against the caller's own tenant, carried on the {@link AgentCallContext.Context}; there
 * is no per-call tenant parameter, so a conversation is single-tenant by construction. Tools carry no
 * authorization by default; a replacement can override the same {@code @Tool} method to validate the
 * caller's access and then delegate to {@code super}. Because such an override drops the
 * {@code @Tool}/{@code @P} annotations, {@link #toolMethod} resolves the annotated method up the class
 * hierarchy — the spec comes from the annotated method while execution dispatches virtually to the
 * override.
 * </p>
 */
@Singleton
@Slf4j
public class ToolCatalog {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final List<AiPlatformTool> platformTools;
    private final List<AiAuthoringTool> authoringTools;
    private final DocsMcpToolProvider docsMcpToolProvider;
    private final AgentToolPermissionEvaluator permissionEvaluator;

    private volatile Map<String, ToolEntry> registry;

    @Inject
    public ToolCatalog(
        final List<AiPlatformTool> platformTools,
        final List<AiAuthoringTool> authoringTools,
        final DocsMcpToolProvider docsMcpToolProvider,
        final AgentToolPermissionEvaluator permissionEvaluator) {
        this.platformTools = platformTools;
        this.authoringTools = authoringTools;
        this.docsMcpToolProvider = docsMcpToolProvider;
        this.permissionEvaluator = permissionEvaluator;
    }

    public record ToolEntry(
        String name,
        ToolSpecification specification,
        ToolExecutor executor,
        AgentToolCall.Kind kind,
        @Nullable AgentToolFamily family,
        AgentWritePolicy writePolicy,
        @Nullable AiTool tool) {
        /** Docs MCP entries carry no tool bean and are outside permission evaluation. */
        public boolean isPermissionEvaluated() {
            return tool != null;
        }

        /** Authoring tools are non-mutating drafts, advertised in every mode. */
        public boolean isAuthoring() {
            return kind == AgentToolCall.Kind.AUTHORING;
        }
    }

    private Map<String, ToolEntry> registry() {
        if (registry != null) {
            return registry;
        }
        synchronized (this) {
            if (registry != null) {
                return registry;
            }
            Map<String, ToolEntry> built = new LinkedHashMap<>();

            for (AiPlatformTool tool : platformTools) {
                register(built, tool, AgentToolCall.Kind.PLATFORM, tool.family(), tool.writePolicy());
            }
            // Authoring tools are drafts-only: no family (they escape mode gating) and never confirmed.
            for (AiAuthoringTool tool : authoringTools) {
                register(built, tool, AgentToolCall.Kind.AUTHORING, null, AgentWritePolicy.AUTO);
            }

            // Docs MCP tools are public documentation reads: no permission and no tenant scoping.
            docsMcpToolProvider.tools().forEach(
                (spec, executor) -> built.put(
                    spec.name(), new ToolEntry(
                        spec.name(), spec, executor, AgentToolCall.Kind.PLATFORM, AgentToolFamily.READ, AgentWritePolicy.AUTO, null
                    )
                )
            );

            this.registry = built;
            return built;
        }
    }

    private void register(final Map<String, ToolEntry> built, final AiTool tool,
        final AgentToolCall.Kind kind, @Nullable final AgentToolFamily family,
        final AgentWritePolicy writePolicy) {
        Method method = toolMethod(tool);

        boolean hasQueryFilter = Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(QueryFilterFormat.class));
        ToolSpecification spec = AiToolSpecifications.toolSpecificationFrom(method);

        // propagateToolExecutionExceptions: let a @Tool throw propagate out of dispatch (instead of
        // langchain4j swallowing it into an opaque "ok" result text) so AgentOrchestrator can record it
        // as an error outcome and feed the message back to the model. QueryFilterToolExecutor already
        // propagates, so both executor paths behave the same on failure.
        ToolExecutor executor = hasQueryFilter
            ? new QueryFilterToolExecutor(tool, method)
            : DefaultToolExecutor.builder()
                .object(tool)
                .originalMethod(method)
                .methodToInvoke(method)
                .propagateToolExecutionExceptions(true)
                .build();
        built.put(spec.name(), new ToolEntry(spec.name(), spec, executor, kind, family, writePolicy, tool));
    }

    public Collection<ToolEntry> entries() {
        return registry().values();
    }

    public Optional<ToolEntry> byName(final String name) {
        return Optional.ofNullable(registry().get(name));
    }

    /**
     * Execute a tool call scoped to the given caller context — the one entry point both the in-process
     * loop and (later) the MCP projection use. Enforces the coarse tool permission against the
     * caller's tenant, then hands the context to the {@code @Tool} method (and any overrides) as a
     * langchain4j managed argument on the invocation. Per-namespace and cross-tenant checks are the
     * individual tool implementations' responsibility.
     *
     * @param request the tool call emitted by the model
     * @param context what the call runs as (tenant, principal, provider, conversation)
     * @return the tool's textual result plus any artefact it produced to publish
     * @throws IllegalArgumentException if the tool name is unknown
     * @throws ToolPermissionDeniedException if the caller lacks the tool's permission
     */
    public DispatchResult dispatch(final ToolExecutionRequest request, final AgentCallContext.Context context) {
        ToolEntry entry = registry().get(request.name());
        if (entry == null) {
            throw new IllegalArgumentException("Unknown tool: '%s'".formatted(request.name()));
        }

        if (entry.isPermissionEvaluated()) {
            // The enforcement point: every front door (in-process loop, later the MCP projection) runs
            // through here, acting as the caller. The ModeProfiles pre-filter is UX only.
            if (!permissionEvaluator.isAllowed(entry, context.tenant(), context.principal())) {
                throw new ToolPermissionDeniedException(entry.name(), context.tenant());
            }
        }

        // Carry the caller context to the @Tool method as a langchain4j managed argument — the executor
        // injects it by type into the tool's AgentCallContext.Context parameter — not a thread-local.
        ToolExecutionResult executed = entry.executor().executeWithContext(stripEmptyOptionalArgs(entry, request), AgentCallContext.into(context));
        // A tool's single output is its return value; if that value is publishable, the caller (the
        // orchestrator) persists and streams the artefact — the tool never reaches back through a side channel.
        ArtefactDraft artefact = executed.result() instanceof PublishableToolResult publishable ? publishable.artefact() : null;
        return new DispatchResult(executed.resultText(), artefact);
    }

    /**
     * The outcome of a tool dispatch: the text handed to the model, plus the artefact to publish when
     * the tool returned a {@link PublishableToolResult} (else {@code null}).
     */
    public record DispatchResult(String text, @Nullable ArtefactDraft artefact) {
    }

    /**
     * Drop arguments the model sent as an empty string {@code ""} whose target parameter is not
     * string-typed. Models routinely emit {@code ""} to mean "I am omitting this optional parameter";
     * for a numeric/boolean/object/array parameter that empty string is never a valid value, and
     * langchain4j's argument coercion fails before the {@code @Tool} method ever runs (e.g. {@code
     * Argument "revision" is not convertible to java.lang.Integer}). Stripping such values lets an
     * optional parameter fall back to its default instead of turning a harmless omission into a tool
     * failure. String and enum parameters are left untouched — {@code ""} may be a legitimate value
     * there, and any genuine problem is better surfaced by the tool itself with an actionable message.
     */
    private static ToolExecutionRequest stripEmptyOptionalArgs(final ToolEntry entry, final ToolExecutionRequest request) {
        String rawArguments = request.arguments();
        if (rawArguments == null || rawArguments.isBlank()
            || !(entry.specification().parameters() instanceof JsonObjectSchema schema)
            || schema.properties() == null) {
            return request;
        }

        Map<String, Object> arguments;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(rawArguments, Map.class);
            arguments = new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            // Not our JSON to fix: leave it for the executor's own coercion to report.
            return request;
        }

        boolean stripped = arguments.entrySet().removeIf(argument ->
            "".equals(argument.getValue()) && isNonStringProperty(schema.properties().get(argument.getKey()))
        );
        if (!stripped) {
            return request;
        }

        try {
            return ToolExecutionRequest.builder()
                .id(request.id())
                .name(request.name())
                .arguments(MAPPER.writeValueAsString(arguments))
                .build();
        } catch (Exception e) {
            log.warn("Could not re-serialize sanitized arguments for tool '{}'; passing through unchanged.", request.name(), e);
            return request;
        }
    }

    /** A schema property for which an empty string is never a valid value — anything but string/enum. */
    private static boolean isNonStringProperty(@Nullable final JsonSchemaElement property) {
        return property != null && !(property instanceof JsonStringSchema) && !(property instanceof JsonEnumSchema);
    }

    /**
     * The {@code @Tool}-annotated method, searched up the class hierarchy: a replacement subclass may
     * override the method without repeating {@code @Tool}/{@code @P}, so the annotated definition
     * lives on the super-class while the bean itself is the subtype. Executing the returned method
     * against the bean dispatches virtually to the override.
     */
    private static Method toolMethod(final Object toolInstance) {
        for (Class<?> type = toolInstance.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException(
            "No @Tool-annotated method found on " + toolInstance.getClass().getName()
        );
    }
}
