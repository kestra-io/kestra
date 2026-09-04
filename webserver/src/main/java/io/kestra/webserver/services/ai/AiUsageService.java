package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.ai.usage.repositories.AiUsageRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

/**
 * Records what AI model calls cost, and reports where that leaves a provider against its ceiling.
 *
 * <p>Recording is unconditional, so switching a limit on reports against history that already exists rather
 * than starting from zero. Enforcement is check-then-charge per model call, which overshoots by at most one
 * call — reserving up front would mean guessing the cost of a response not yet generated.
 */
@Singleton
@Slf4j
public class AiUsageService {
    private final AiUsageRepositoryInterface repository;
    private final AiServiceManager aiServiceManager;
    private final AiTokenUsageReader tokenUsageReader;
    private final AiUsageMetrics metrics;

    @Inject
    public AiUsageService(
        final AiUsageRepositoryInterface repository,
        final AiServiceManager aiServiceManager,
        final AiTokenUsageReader tokenUsageReader,
        final AiUsageMetrics metrics) {
        this.repository = repository;
        this.aiServiceManager = aiServiceManager;
        this.tokenUsageReader = tokenUsageReader;
        this.metrics = metrics;
    }

    /**
     * Records one model call, or nothing when the provider reported no usage. Never throws — a turn that
     * succeeded must not fail because its bookkeeping could not be written — but logs at warn, since a dropped
     * record undercounts spend.
     */
    public void record(
        final String tenant,
        final String providerId,
        @Nullable final String userId,
        @Nullable final String model,
        @Nullable final TokenUsage usage) {
        String resolvedProviderId = resolveProviderId(providerId);
        AiUsageTotals counts = tokenUsageReader.read(usage);

        metrics.modelCall(resolvedProviderId, tenant, model, counts);

        if (counts.promptTokens() == 0 && counts.completionTokens() == 0 && counts.thoughtTokens() == 0) {
            return;
        }

        try {
            repository.save(new AiUsage(
                IdUtils.create(),
                tenant,
                resolvedProviderId,
                userId,
                model,
                Instant.now(),
                counts.promptTokens(),
                counts.cachedPromptTokens(),
                counts.completionTokens(),
                counts.thoughtTokens()
            ));
        } catch (Exception e) {
            metrics.recordFailed(resolvedProviderId, tenant, model);
            log.warn("Could not record AI usage for provider '{}': {}", providerId, e.getMessage(), e);
        }
    }

    /**
     * Where {@code userId} stands against {@code providerId}'s ceiling, for display and enforcement alike.
     *
     * <p>Returns {@link AiUsageStatus#disabled} when the provider has no limits switched on, which is the
     * default. Takes no tenant, unlike {@link #record}: the provider key belongs to the installation, so both
     * ceilings count every tenant's spend against it.
     *
     * @param userId the caller, or null where there is no user identity in the agent path — in which case only
     *               the installation-wide axis is evaluated
     */
    public AiUsageStatus status(@Nullable final String providerId, @Nullable final String userId) {
        String resolvedProviderId = resolveProviderId(providerId);
        AiServiceInterface service = aiServiceManager.getAiService(providerId);
        if (service == null) {
            return AiUsageStatus.disabled(resolvedProviderId);
        }

        Optional<AiUsageLimitConfiguration> declared = service.usageLimit();
        if (declared.isEmpty()) {
            return AiUsageStatus.disabled(resolvedProviderId);
        }

        AiUsageLimitConfiguration limit = declared.get();

        Instant now = Instant.now();
        Instant from = limit.windowStart(now);
        AiUsageStatus.Axis global = AiUsageStatus.Axis.of(
            limit.weigh(repository.totals(resolvedProviderId, from)),
            limit.maxWeight()
        );
        AiUsageStatus.Axis user = userId == null
            ? null
            : AiUsageStatus.Axis.of(
                limit.weigh(repository.totalsForUser(resolvedProviderId, userId, from)),
                limit.userMaxWeight()
            );

        // The period end is only worth reporting while something is exhausted; on screen the rest of the time
        // it reads as a deadline.
        boolean exhausted = global.exceeded() || (user != null && user.exceeded());

        AiUsageStatus status = new AiUsageStatus(
            resolvedProviderId,
            true,
            from,
            exhausted ? limit.windowEnd(now) : null,
            global,
            user,
            limit.warningThresholdPercent()
        );

        metrics.observed(status);

        return status;
    }

    /**
     * Books a turn refused for want of allowance. Counted here rather than wherever a status is read, since
     * {@link #status} is also what the usage endpoint answers and polling it is not a refusal.
     */
    public void recordRefusal(final AiUsageStatus status) {
        metrics.refused(status);
    }

    /**
     * The provider a turn will actually run against, so recorded rows and queried totals agree. Recording an
     * unnamed provider as "none" would split one provider's history across two keys.
     */
    private String resolveProviderId(@Nullable final String providerId) {
        return providerId != null ? providerId : aiServiceManager.getDefaultProviderId();
    }
}
