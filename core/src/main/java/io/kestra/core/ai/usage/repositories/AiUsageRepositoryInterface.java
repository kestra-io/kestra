package io.kestra.core.ai.usage.repositories;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * Durable record of what AI model calls have cost this installation. Persisted rather than in memory so that a
 * horizontally scaled webserver agrees on remaining budget, and so a user can see their usage before spending a
 * turn to find out.
 */
public interface AiUsageRepositoryInterface {
    /** Records one model call. Called for every provider, whether or not limits are enabled. */
    AiUsage save(AiUsage usage);

    /**
     * Totals for a provider across every caller and every tenant — the axis an installation-wide ceiling
     * applies to. Not scoped by tenant: the provider key is held by the installation and billed to it, so the
     * ceiling protecting it has to see everything spent against it.
     *
     * @param from inclusive lower bound; the caller decides the window
     */
    AiUsageTotals totals(String providerId, Instant from);

    /**
     * Totals for one caller across every tenant, or across callers with no user when {@code userId} is null.
     *
     * <p>Also unscoped by tenant: a user is an instance-level identity that can reach several tenants, so a
     * per-tenant reading would hand the same person a fresh allowance in each. The null case is not an edge
     * case — where there is no user identity in the agent path, usage is recorded unattributed.
     */
    AiUsageTotals totalsForUser(String providerId, @Nullable String userId, Instant from);
}
