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
 * <p>Recording is unconditional and applies to every provider, whether or not it has limits switched on. So
 * turning a limit on reports against history that already exists, rather than starting from zero and looking
 * wrong for a window; and an operator running their own key gets a spend figure without opting into anything.
 *
 * <p>Enforcement is check-then-charge, per model call. The overshoot that allows is bounded by one call, which is
 * bounded in turn by the provider's own output cap — and the alternative, reserving before the call, would have to
 * guess the cost of a response that has not been generated yet.
 */
@Singleton
@Slf4j
public class AiUsageService {
    private final AiUsageRepositoryInterface repository;
    private final AiServiceManager aiServiceManager;
    private final AiTokenUsageReader tokenUsageReader;

    @Inject
    public AiUsageService(
        final AiUsageRepositoryInterface repository,
        final AiServiceManager aiServiceManager,
        final AiTokenUsageReader tokenUsageReader) {
        this.repository = repository;
        this.aiServiceManager = aiServiceManager;
        this.tokenUsageReader = tokenUsageReader;
    }

    /**
     * Records one model call, or nothing when the provider reported no usage.
     *
     * <p>Never throws: a turn that succeeded must not be failed because its bookkeeping could not be written, and
     * the user would have no way to act on the failure anyway. A dropped record undercounts spend, so the failure
     * is logged at warn rather than swallowed.
     */
    public void record(
        final String tenant,
        final String providerId,
        @Nullable final String userId,
        @Nullable final String model,
        @Nullable final TokenUsage usage) {
        AiUsageTotals counts = tokenUsageReader.read(usage);
        if (counts.promptTokens() == 0 && counts.completionTokens() == 0 && counts.thoughtTokens() == 0) {
            return;
        }

        try {
            repository.save(new AiUsage(
                IdUtils.create(),
                tenant,
                resolveProviderId(providerId),
                userId,
                model,
                Instant.now(),
                counts.promptTokens(),
                counts.cachedPromptTokens(),
                counts.completionTokens(),
                counts.thoughtTokens()
            ));
        } catch (Exception e) {
            log.warn("Could not record AI usage for provider '{}': {}", providerId, e.getMessage(), e);
        }
    }

    /**
     * Where {@code userId} stands against {@code providerId}'s ceiling, for display and for enforcement alike.
     *
     * <p>Returns {@link AiUsageStatus#disabled} when the provider has no limits switched on, which is the default:
     * usage is still being recorded, but there is nothing an operator asked to be shown or held to.
     *
     * @param userId the caller, or null where the edition has no user identity in the agent path — in which case
     *               only the installation-wide axis is evaluated, since every row would fall in the same bucket
     */
    public AiUsageStatus status(final String tenant, @Nullable final String providerId, @Nullable final String userId) {
        String resolvedProviderId = resolveProviderId(providerId);
        AiServiceInterface service = aiServiceManager.getAiService(providerId);
        if (service == null) {
            return AiUsageStatus.disabled(resolvedProviderId);
        }

        // Read defensively: this runs before every model call, on the path of every chat request, and an
        // implementation that answers nothing means it declares no limit — not that Copilot should 500.
        Optional<AiUsageLimitConfiguration> declared =
            Optional.ofNullable(service.usageLimit()).orElseGet(Optional::empty);
        if (declared.isEmpty()) {
            return AiUsageStatus.disabled(resolvedProviderId);
        }

        AiUsageLimitConfiguration limit = declared.get();

        Instant now = Instant.now();
        Instant from = limit.windowStart(now);
        AiUsageStatus.Axis global = AiUsageStatus.Axis.of(
            limit.weigh(repository.totals(tenant, resolvedProviderId, from)),
            limit.maxWeight()
        );
        AiUsageStatus.Axis user = userId == null
            ? null
            : AiUsageStatus.Axis.of(
                limit.weigh(repository.totalsForUser(tenant, resolvedProviderId, userId, from)),
                limit.userMaxWeight()
            );

        // Only worth answering while something is exhausted: the end of a period a caller is not held by is not
        // news, and a date on the screen the whole time would read as one.
        boolean exhausted = global.exceeded() || (user != null && user.exceeded());

        return new AiUsageStatus(
            resolvedProviderId,
            true,
            from,
            exhausted ? limit.windowEnd(now) : null,
            global,
            user,
            limit.warningThresholdPercent()
        );
    }

    /**
     * The provider a turn will actually run against, so recorded rows and queried totals agree.
     *
     * <p>A caller may leave the provider unnamed and get the default one; recording that as "no provider" would
     * split one provider's history across two keys and make its ceiling unenforceable.
     */
    private String resolveProviderId(@Nullable final String providerId) {
        return providerId != null ? providerId : aiServiceManager.getDefaultProviderId();
    }
}
