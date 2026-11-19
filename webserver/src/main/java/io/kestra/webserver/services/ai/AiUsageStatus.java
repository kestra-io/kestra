package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * Where a provider stands against its ceiling — one type for both what the API answers and what the
 * orchestrator enforces, so the figure shown and the figure enforced cannot drift apart.
 *
 * @param providerId              the provider these figures are for, resolved from the default when the caller
 *                                named none
 * @param enabled                 whether this provider has limits switched on; when false every other field is
 *                                empty
 * @param windowStart             the lower bound the totals were summed from
 * @param availableAt             when the current period ends and spend starts again, absent while nothing is
 *                                exhausted
 * @param global                  spend across every caller and every tenant, which is what the key is billed for
 * @param user                    spend by the calling user, likewise across every tenant, absent where there is
 *                                no user identity
 * @param warningThresholdPercent the remaining percentage below which a client should warn
 */
public record AiUsageStatus(
    @Nullable String providerId,
    boolean enabled,
    @Nullable Instant windowStart,
    @Nullable Instant availableAt,
    @Nullable Axis global,
    @Nullable Axis user,
    int warningThresholdPercent
) {
    /**
     * One ceiling and what has been spent against it.
     *
     * @param maxWeight the ceiling, or zero when this axis has none configured — reported for information and
     *                  never exceeded
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

    /** What a provider reports until limits are switched on: usage is recorded, but there is nothing to show. */
    public static AiUsageStatus disabled(@Nullable String providerId) {
        return new AiUsageStatus(providerId, false, null, null, null, null, 0);
    }

    @JsonProperty("exceeded")
    public boolean isExceeded() {
        return (global != null && global.exceeded()) || (user != null && user.exceeded());
    }

    /** True once either axis is inside its warning threshold, so a client has one flag rather than two sums to compare. */
    @JsonProperty("warning")
    public boolean isWarning() {
        return remainingPercent() <= warningThresholdPercent;
    }

    @JsonProperty("remainingPercent")
    public int remainingPercent() {
        int remaining = global == null ? 100 : global.remainingPercent();
        return user == null ? remaining : Math.min(remaining, user.remainingPercent());
    }

    /**
     * Why a turn is being refused, naming the axis that is exhausted: a personal allowance is something the
     * user waits out, an installation-wide one is not about them at all.
     */
    public String exceededMessage() {
        if (user != null && user.exceeded()) {
            return "You have reached your AI usage limit (%d of %d weighted tokens since %s).%s"
                .formatted(user.weight(), user.maxWeight(), windowStart, freesUpSentence());
        }
        if (global != null && global.exceeded()) {
            return "This installation has reached its AI usage limit (%d of %d weighted tokens since %s).%s Ask an administrator to raise kestra.ai usage limits."
                .formatted(global.weight(), global.maxWeight(), windowStart, freesUpSentence());
        }
        return "AI usage is within its limits.";
    }

    /** When the caller can run again, omitted entirely when the period end is not known — a wrong date is worse than none. */
    private String freesUpSentence() {
        return availableAt == null ? "" : " It resets on %s.".formatted(availableAt);
    }
}
