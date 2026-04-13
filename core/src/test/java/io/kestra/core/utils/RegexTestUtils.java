package io.kestra.core.utils;

import java.time.Duration;

/**
 * Test helper that delegates to package-private {@link RegexUtils#resetInit()}.
 * Lives in the same package so external test classes can reset the timeout for ReDoS tests.
 */
public final class RegexTestUtils {

    private RegexTestUtils() {
    }

    /**
     * Resets the initialized flag and sets a new timeout.
     */
    public static void resetAndSetTimeout(Duration timeout) {
        RegexUtils.resetInit();
        RegexUtils.setTimeout(timeout);
    }

    /**
     * Resets the initialized flag and restores the default timeout.
     */
    public static void reset() {
        RegexUtils.resetInit();
    }
}
