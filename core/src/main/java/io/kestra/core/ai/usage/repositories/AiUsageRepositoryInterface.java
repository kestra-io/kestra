package io.kestra.core.ai.usage.repositories;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * Durable record of what AI model calls have cost this installation.
 *
 * <p>Persisted rather than held in memory for two reasons that in-memory state cannot satisfy: a horizontally
 * scaled webserver would otherwise have as many opinions about remaining budget as it has nodes, and a user
 * has to be able to see their usage before running a turn rather than as a side effect of one.
 */
public interface AiUsageRepositoryInterface {
    /** Records one model call. Called for every provider, whether or not limits are enabled. */
    AiUsage save(AiUsage usage);

    /**
     * Totals for a provider across every caller — the axis an installation-wide ceiling applies to.
     *
     * @param from inclusive lower bound; the caller decides the window, so a daily limit and a monthly report
     *             read the same store
     */
    AiUsageTotals totals(String tenant, String providerId, Instant from);

    /**
     * Totals for one caller, or across callers with no user when {@code userId} is null.
     *
     * <p>The null case is not an edge case: OSS has no user identity in the agent path at all, so its usage is
     * recorded unattributed and only the installation-wide axis can bind.
     */
    AiUsageTotals totalsForUser(String tenant, String providerId, @Nullable String userId, Instant from);
}
