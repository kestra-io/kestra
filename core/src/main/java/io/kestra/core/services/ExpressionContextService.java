package io.kestra.core.services;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.flows.Flow;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that builds a categorized map of available Pebble expressions for a given context, from the
 * {@link ExpressionContributor} beans of the server.
 * <p>
 * Used by:
 * <ul>
 * <li>The {@code POST /flows/expressions} endpoint: No-Code editor autocompletion</li>
 * <li>AI Copilot prompt builders: format via {@code PebbleExpressionsFormatter.format(context.toDisplayNameMap())}</li>
 * </ul>
 * Expressions are returned <b>without</b> the {@code {{ }}} delimiters.
 */
@Singleton
@Slf4j
public class ExpressionContextService {
    private final Map<ExpressionCategory, ExpressionContributor> contributors = new EnumMap<>(ExpressionCategory.class);

    @Inject
    public ExpressionContextService(List<ExpressionContributor> contributors) {
        for (ExpressionContributor contributor : contributors) {
            ExpressionContributor previous = this.contributors.put(contributor.category(), contributor);
            if (previous != null) {
                throw new KestraRuntimeException(
                    "Both '%s' and '%s' contribute the '%s' expressions; replace one with @Replaces instead.".formatted(
                        previous.getClass().getName(), contributor.getClass().getName(), contributor.category()
                    )
                );
            }
        }
    }

    /**
     * Builds the expressions available without a flow, which are the filters and functions.
     * <p>
     * Suitable as a fallback when no flow reference is available (e.g. TestSuite generation
     * when the referenced flow cannot be resolved).
     */
    public ExpressionContext buildGlobalExpressionContext() {
        return build(ExpressionRequest.global(), Set.of());
    }

    /**
     * Builds the full flow-scoped expression context for flow generation and the
     * {@code POST /flows/expressions} No-Code editor endpoint.
     *
     * @param flow the parsed flow
     * @param taskId optional task ID to scope task outputs to topological predecessors only
     */
    public ExpressionContext buildExpressionContext(Flow flow, @Nullable String taskId) {
        return buildExpressionContext(flow, taskId, Set.of());
    }

    /**
     * Same as {@link #buildExpressionContext(Flow, String)}, without the excluded categories, whose
     * contributors are not called at all.
     */
    public ExpressionContext buildExpressionContext(Flow flow, @Nullable String taskId, Set<ExpressionCategory> excludedCategories) {
        return build(new ExpressionRequest(flow, taskId), excludedCategories);
    }

    private ExpressionContext build(ExpressionRequest request, Set<ExpressionCategory> excludedCategories) {
        ExpressionContext.Builder builder = ExpressionContext.builder();
        for (ExpressionContributor contributor : contributors.values()) {
            if (excludedCategories.contains(contributor.category())) {
                continue;
            }
            builder.put(contributor.category(), contribute(contributor, request));
        }
        return builder.build();
    }

    private static List<String> contribute(ExpressionContributor contributor, ExpressionRequest request) {
        try {
            return contributor.contribute(request);
        } catch (IOException | RuntimeException e) {
            log.warn(
                "Could not build the {} expressions of namespace {}: {}",
                contributor.category(), request.flow() == null ? null : request.flow().getNamespace(), e.getMessage()
            );
            return List.of();
        }
    }
}
