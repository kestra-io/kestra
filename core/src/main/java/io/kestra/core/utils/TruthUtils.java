package io.kestra.core.utils;

import java.util.List;

/**
 * Utility class for evaluating condition truthiness.
 * <p>
 * Note that {@code null} is considered neither truthy nor falsy: both {@link #isTruthy(String)}
 * and {@link #isFalsy(String)} return {@code false} for {@code null}. Callers that require
 * {@code null} to be treated as non-affirmative should use {@code !isTruthy(condition)}.
 */
public final class TruthUtils {
    private static final List<String> FALSE_VALUES = List.of("false", "0", "-0", "");

    private TruthUtils() {
    }

    /**
     * Checks if the condition is truthy (non-null and not in {@code ["false", "0", "-0", ""]}).
     */
    public static boolean isTruthy(String condition) {
        return condition != null && !FALSE_VALUES.contains(condition.trim());
    }

    /**
     * Checks if the condition is explicitly falsy (non-null and in {@code ["false", "0", "-0", ""]}).
     * Returns {@code false} when the condition is {@code null}.
     */
    public static boolean isFalsy(String condition) {
        return condition != null && FALSE_VALUES.contains(condition.trim());
    }
}
