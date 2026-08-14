package io.kestra.webserver.services.ai;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * Where a provider stands against its ceiling, as both the API answers it and the orchestrator enforces it.
 *
 * <p>One type for both on purpose: a figure shown to a user that a different code path then enforces is a bug
 * waiting to happen, and "why does it say 40% left and still refuse?" is not a question worth being able to ask.
 *
 * @param providerId              the provider these figures are for, resolved from the default when the caller
 *                                named none
 * @param enabled                 whether this provider has limits switched on at all; when false every other
 *                                field is empty, because a disabled limit is neither shown nor enforced
 * @param windowStart             the lower bound the totals were summed from
 * @param global                  spend across every caller
 * @param user                    spend by the calling user, absent where the edition has no user identity
 * @param warningThresholdPercent the remaining percentage below which a client should warn
 */
public record AiUsageStatus(
    @Nullable String providerId,
    boolean enabled,
    @Nullable Instant windowStart,
    @Nullable Axis global,
    @Nullable Axis user,
    int warningThresholdPercent
) {
    /**
     * One ceiling and what has been spent against it.
     *
     * @param maxWeight the ceiling, or zero when this axis has none configured — in which case it is reported
     *                  for information and never exceeded
     */
    public record Axis(long weight, long maxWeight, int remainingPercent, boolean exceeded) {
        static Axis of(long weight, long maxWeight) {
            if (maxWeight <= 0) {
                return new Axis(weight, 0, 100, false);
            }
            long remaining = Math.max(0, maxWeight - weight);
            return new Axis(weight, maxWeight, (int) (remaining * 100 / maxWeight), weight >= maxWeight);
        }
    }

    /** What every provider reports until someone switches limits on: recorded, but nothing to show. */
    public static AiUsageStatus disabled(@Nullable String providerId) {
        return new AiUsageStatus(providerId, false, null, null, null, 0);
    }

    public boolean isExceeded() {
        return (global != null && global.exceeded()) || (user != null && user.exceeded());
    }

    /** True once either axis is inside its warning threshold, so a client has one flag rather than two sums. */
    public boolean isWarning() {
        return remainingPercent() <= warningThresholdPercent;
    }

    /** The tightest axis, which is the only one worth showing a user a single number for. */
    public int remainingPercent() {
        int remaining = global == null ? 100 : global.remainingPercent();
        return user == null ? remaining : Math.min(remaining, user.remainingPercent());
    }

    /**
     * Why a turn is being refused, naming the axis that is exhausted.
     *
     * <p>The distinction matters to whoever reads it: an exhausted personal allowance is something the user waits
     * out or asks an admin about, while an exhausted installation-wide one is not about them at all.
     */
    public String exceededMessage() {
        if (user != null && user.exceeded()) {
            return "You have reached your AI usage limit (%d of %d weighted tokens since %s). It will free up as the window moves on."
                .formatted(user.weight(), user.maxWeight(), windowStart);
        }
        if (global != null && global.exceeded()) {
            return "This installation has reached its AI usage limit (%d of %d weighted tokens since %s). Ask an administrator to raise kestra.ai usage limits."
                .formatted(global.weight(), global.maxWeight(), windowStart);
        }
        return "AI usage is within its limits.";
    }
}
