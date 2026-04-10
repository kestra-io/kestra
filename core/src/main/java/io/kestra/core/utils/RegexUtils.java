package io.kestra.core.utils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utilities for running regex operations with a timeout guard to prevent ReDoS
 * (catastrophic backtracking) attacks.
 *
 * <p>All methods wrap the input {@link CharSequence} so that every {@code charAt()} call
 * checks a deadline. If the deadline is exceeded, a {@link RegexTimeoutException} is thrown,
 * terminating the backtracking without needing a separate thread.</p>
 */
public final class RegexUtils {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static volatile Duration timeout = DEFAULT_TIMEOUT;

    private RegexUtils() {
    }

    /**
     * Sets the global regex timeout. Can only be called once (typically by {@link RegexConfiguration}
     * at startup). Subsequent calls are silently ignored.
     *
     * @param duration the maximum duration for any regex operation.
     */
    public static void setTimeout(Duration duration) {
        if (initialized.compareAndSet(false, true)) {
            timeout = duration;
        }
    }

    /**
     * Returns the current regex timeout.
     */
    public static Duration getTimeout() {
        return timeout;
    }

    /**
     * Resets the initialized flag so {@link #setTimeout(Duration)} can be called again.
     * This is intended for tests only.
     */
    @VisibleForTesting
    static void resetInit() {
        initialized.set(false);
        timeout = DEFAULT_TIMEOUT;
    }

    /**
     * Tests whether the input matches the given regex pattern.
     *
     * @param regex the regex pattern string.
     * @param input the input to test.
     * @return {@code true} if the full input matches the pattern.
     * @throws RegexTimeoutException if the operation exceeds the configured timeout.
     */
    public static boolean matches(String regex, CharSequence input) {
        return matches(regex, input, timeout);
    }

    /**
     * Tests whether the input matches the given regex pattern with an explicit timeout.
     *
     * @param regex   the regex pattern string.
     * @param input   the input to test.
     * @param timeout the maximum duration for this operation.
     * @return {@code true} if the full input matches the pattern.
     * @throws RegexTimeoutException if the operation exceeds the given timeout.
     */
    public static boolean matches(String regex, CharSequence input, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        return matches(pattern, input, timeout);
    }

    /**
     * Tests whether the input matches the given compiled pattern.
     *
     * @param pattern the compiled regex pattern.
     * @param input   the input to test.
     * @return {@code true} if the full input matches the pattern.
     * @throws RegexTimeoutException if the operation exceeds the configured timeout.
     */
    public static boolean matches(Pattern pattern, CharSequence input) {
        return matches(pattern, input, timeout);
    }

    /**
     * Tests whether the input matches the given compiled pattern with an explicit timeout.
     *
     * @param pattern the compiled regex pattern.
     * @param input   the input to test.
     * @param timeout the maximum duration for this operation.
     * @return {@code true} if the full input matches the pattern.
     * @throws RegexTimeoutException if the operation exceeds the given timeout.
     */
    public static boolean matches(Pattern pattern, CharSequence input, Duration timeout) {
        return matcher(pattern, input, timeout).matches();
    }

    /**
     * Creates a {@link Matcher} with timeout protection on the input.
     *
     * @param pattern the compiled regex pattern.
     * @param input   the input to match against.
     * @return a matcher that will throw {@link RegexTimeoutException} if the timeout is exceeded.
     */
    public static Matcher matcher(Pattern pattern, CharSequence input) {
        return matcher(pattern, input, timeout);
    }

    /**
     * Creates a {@link Matcher} with timeout protection on the input using an explicit timeout.
     *
     * @param pattern the compiled regex pattern.
     * @param input   the input to match against.
     * @param timeout the maximum duration for this operation.
     * @return a matcher that will throw {@link RegexTimeoutException} if the given timeout is exceeded.
     */
    public static Matcher matcher(Pattern pattern, CharSequence input, Duration timeout) {
        return pattern.matcher(new TimeoutCharSequence(input, timeout));
    }

    /**
     * Replaces all occurrences of the regex in the input string.
     *
     * @param input       the input string.
     * @param regex       the regex pattern string.
     * @param replacement the replacement string.
     * @return the result with all matches replaced.
     * @throws RegexTimeoutException if the operation exceeds the configured timeout.
     */
    public static String replaceAll(String input, String regex, String replacement) {
        return replaceAll(input, regex, replacement, timeout);
    }

    /**
     * Replaces all occurrences of the regex in the input string with an explicit timeout.
     *
     * @param input       the input string.
     * @param regex       the regex pattern string.
     * @param replacement the replacement string.
     * @param timeout     the maximum duration for this operation.
     * @return the result with all matches replaced.
     * @throws RegexTimeoutException if the operation exceeds the given timeout.
     */
    public static String replaceAll(String input, String regex, String replacement, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        return matcher(pattern, input, timeout).replaceAll(replacement);
    }

    /**
     * Exception thrown when a regex operation exceeds the configured timeout.
     */
    public static class RegexTimeoutException extends RuntimeException {
        public RegexTimeoutException(Duration timeout) {
            super("Regex operation timed out after " + timeout.toMillis() + "ms. " +
                "The pattern may be vulnerable to catastrophic backtracking (ReDoS).");
        }
    }

    /**
     * A {@link CharSequence} wrapper that checks a deadline on every Nth {@code charAt()} call.
     * If the deadline is exceeded, it throws a {@link RegexTimeoutException}.
     */
    private static final class TimeoutCharSequence implements CharSequence {

        private static final int CHECK_INTERVAL = 1024;

        private final CharSequence delegate;
        private final long deadlineNanos;
        private final Duration timeout;
        private int counter;

        TimeoutCharSequence(CharSequence delegate, Duration timeout) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (++counter % CHECK_INTERVAL == 0 && System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException(timeout);
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            // Return a new TimeoutCharSequence sharing the same deadline
            return new TimeoutCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        /**
         * Constructor that reuses an existing deadline (for subSequence).
         */
        private TimeoutCharSequence(CharSequence delegate, long deadlineNanos) {
            this.delegate = delegate;
            this.timeout = Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
            this.deadlineNanos = deadlineNanos;
        }
    }
}
