package io.kestra.core.runners.pebble;

/**
 * Shared helpers for enforcing Pebble rendering resource limits.
 * <p>
 * Functions and filters that can allocate a large amount of memory from a single call (a whole
 * collection or string materialized before it is ever written) validate their requested size upfront
 * through this helper, failing with a bounded {@link RenderLimitExceededException} rather than
 * delegating and risking heap exhaustion.
 */
public final class RenderLimits {
    private RenderLimits() {
    }

    /**
     * Ensures a requested size stays at or below a maximum, throwing otherwise.
     *
     * @param requested     the size the expression would produce
     * @param max           the maximum allowed size
     * @param messageFormat a message template receiving {@code max} then {@code requested} as its two
     *                      {@code %d} placeholders
     * @throws RenderLimitExceededException if {@code requested > max}
     */
    public static void ensureAtMost(final long requested, final long max, final String messageFormat) {
        if (requested > max) {
            throw new RenderLimitExceededException(messageFormat.formatted(max, requested));
        }
    }
}
