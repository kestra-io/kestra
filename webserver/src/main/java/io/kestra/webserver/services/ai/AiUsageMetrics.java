package io.kestra.webserver.services.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.metrics.MetricRegistry;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Publishes what the Copilot spends, and where that leaves it against its ceiling.
 *
 * <p>Counters are written per model call, from the same counts {@link AiUsageService} stores, so a dashboard and
 * the stored history cannot disagree. Gauges are refreshed whenever a status is computed, which happens before
 * every model call as well as on the usage endpoint — they are a last-observed value, not a live query, since
 * summing a window costs a database round trip and a scrape must not trigger one.
 *
 * <p>Only the installation-wide axis is gauged. A per-user gauge would carry one series per user, which is
 * unbounded; the tokens a single user spends are still visible on the counters, aggregated by provider.
 */
@Singleton
public class AiUsageMetrics {
    /** Stands in for a tag value the provider did not report, since Micrometer rejects a null tag. */
    private static final String UNKNOWN = "__none__";
    private static final String AXIS_GLOBAL = "global";
    private static final String AXIS_USER = "user";

    private final MetricRegistry metricRegistry;
    private final Map<String, AtomicLong> weightByProvider = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> weightLimitByProvider = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> remainingRatioByProvider = new ConcurrentHashMap<>();

    @Inject
    public AiUsageMetrics(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    /**
     * One model call and what it consumed. Counted even when every count is zero — a provider that reports no
     * usage still made a call, and a call rate that silently omits them reads as an idle Copilot.
     */
    public void modelCall(
        final String providerId,
        @Nullable final String tenant,
        @Nullable final String model,
        final AiUsageTotals counts) {
        String[] tags = tags(providerId, tenant, model);

        metricRegistry.counter(
            MetricRegistry.METRIC_AI_MODEL_CALL_TOTAL,
            MetricRegistry.METRIC_AI_MODEL_CALL_TOTAL_DESCRIPTION,
            tags
        ).increment();

        increment(MetricRegistry.METRIC_AI_TOKEN_PROMPT_TOTAL, MetricRegistry.METRIC_AI_TOKEN_PROMPT_TOTAL_DESCRIPTION, counts.promptTokens(), tags);
        increment(MetricRegistry.METRIC_AI_TOKEN_PROMPT_CACHED_TOTAL, MetricRegistry.METRIC_AI_TOKEN_PROMPT_CACHED_TOTAL_DESCRIPTION, counts.cachedPromptTokens(), tags);
        increment(MetricRegistry.METRIC_AI_TOKEN_COMPLETION_TOTAL, MetricRegistry.METRIC_AI_TOKEN_COMPLETION_TOTAL_DESCRIPTION, counts.completionTokens(), tags);
        increment(MetricRegistry.METRIC_AI_TOKEN_THOUGHT_TOTAL, MetricRegistry.METRIC_AI_TOKEN_THOUGHT_TOTAL_DESCRIPTION, counts.thoughtTokens(), tags);
    }

    /** A call whose usage could not be stored, and which therefore undercounts every stored total. */
    public void recordFailed(final String providerId, @Nullable final String tenant, @Nullable final String model) {
        metricRegistry.counter(
            MetricRegistry.METRIC_AI_USAGE_RECORD_FAILED_TOTAL,
            MetricRegistry.METRIC_AI_USAGE_RECORD_FAILED_TOTAL_DESCRIPTION,
            tags(providerId, tenant, model)
        ).increment();
    }

    /**
     * Refreshes a provider's gauges from a freshly computed status. A status with no limits switched on carries
     * no figures, so it is ignored rather than published as a ceiling of zero.
     */
    public void observed(final AiUsageStatus status) {
        if (!status.enabled() || status.providerId() == null || status.global() == null) {
            return;
        }

        weight(status.providerId()).set(status.global().weight());
        weightLimit(status.providerId()).set(status.global().maxWeight());
        remainingRatio(status.providerId()).set(status.remainingPercent() / 100.0);
    }

    /** A turn stopped because the provider is out of allowance, tagged with the axis that ran out. */
    public void refused(final AiUsageStatus status) {
        String providerId = status.providerId() == null ? UNKNOWN : status.providerId();
        boolean userAxis = status.user() != null && status.user().exceeded();

        metricRegistry.counter(
            MetricRegistry.METRIC_AI_USAGE_REFUSED_TOTAL,
            MetricRegistry.METRIC_AI_USAGE_REFUSED_TOTAL_DESCRIPTION,
            MetricRegistry.TAG_AI_PROVIDER_ID, providerId,
            MetricRegistry.TAG_AI_USAGE_AXIS, userAxis ? AXIS_USER : AXIS_GLOBAL
        ).increment();
    }

    private void increment(final String name, final String description, final long amount, final String[] tags) {
        metricRegistry.counter(name, description, tags).increment(amount);
    }

    private static String[] tags(final String providerId, @Nullable final String tenant, @Nullable final String model) {
        return new String[] {
            MetricRegistry.TAG_AI_PROVIDER_ID, providerId == null ? UNKNOWN : providerId,
            MetricRegistry.TAG_AI_MODEL, model == null ? UNKNOWN : model,
            MetricRegistry.TAG_TENANT_ID, tenant == null ? UNKNOWN : tenant
        };
    }

    private AtomicLong weight(final String providerId) {
        return weightByProvider.computeIfAbsent(providerId, id -> {
            AtomicLong weight = new AtomicLong();
            metricRegistry.gauge(
                MetricRegistry.METRIC_AI_USAGE_WEIGHT, MetricRegistry.METRIC_AI_USAGE_WEIGHT_DESCRIPTION, weight, gaugeTags(id)
            );
            return weight;
        });
    }

    private AtomicLong weightLimit(final String providerId) {
        return weightLimitByProvider.computeIfAbsent(providerId, id -> {
            AtomicLong limit = new AtomicLong();
            metricRegistry.gauge(
                MetricRegistry.METRIC_AI_USAGE_WEIGHT_LIMIT, MetricRegistry.METRIC_AI_USAGE_WEIGHT_LIMIT_DESCRIPTION, limit, gaugeTags(id)
            );
            return limit;
        });
    }

    /**
     * The remaining share as a fraction, held behind a supplier since a gauge needs a mutable {@link Number} to
     * read on every scrape and there is no atomic double in the JDK.
     */
    private AtomicReference<Double> remainingRatio(final String providerId) {
        return remainingRatioByProvider.computeIfAbsent(providerId, id -> {
            AtomicReference<Double> ratio = new AtomicReference<>(1.0);
            metricRegistry.gauge(
                MetricRegistry.METRIC_AI_USAGE_REMAINING_RATIO, MetricRegistry.METRIC_AI_USAGE_REMAINING_RATIO_DESCRIPTION,
                ratio::get, gaugeTags(id)
            );
            return ratio;
        });
    }

    private static String[] gaugeTags(final String providerId) {
        return new String[] {
            MetricRegistry.TAG_AI_PROVIDER_ID, providerId,
            MetricRegistry.TAG_AI_USAGE_AXIS, AXIS_GLOBAL
        };
    }
}
