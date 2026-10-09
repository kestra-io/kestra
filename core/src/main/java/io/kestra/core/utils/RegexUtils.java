package io.kestra.core.utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utilities for running regex operations with a timeout guard to prevent ReDoS
 * (catastrophic backtracking) attacks.
 *
 * <p>
 * All methods wrap the input {@link CharSequence} so that every {@code charAt()} call
 * checks a deadline. If the deadline is exceeded, a {@link RegexTimeoutException} is thrown,
 * terminating the backtracking without needing a separate thread.
 * </p>
 */
public final class RegexUtils {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Maximum length allowed for a user-supplied regex pattern that is executed by the database engine.
     * Longer patterns are rejected without evaluation.
     */
    public static final int MAX_USER_REGEX_LENGTH = 250;

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
     * @param regex the regex pattern string.
     * @param input the input to test.
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
     * @param input the input to test.
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
     * @param input the input to test.
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
     * @param input the input to match against.
     * @return a matcher that will throw {@link RegexTimeoutException} if the timeout is exceeded.
     */
    public static Matcher matcher(Pattern pattern, CharSequence input) {
        return matcher(pattern, input, timeout);
    }

    /**
     * Creates a {@link Matcher} with timeout protection on the input using an explicit timeout.
     *
     * @param pattern the compiled regex pattern.
     * @param input the input to match against.
     * @param timeout the maximum duration for this operation.
     * @return a matcher that will throw {@link RegexTimeoutException} if the given timeout is exceeded.
     */
    public static Matcher matcher(Pattern pattern, CharSequence input, Duration timeout) {
        return pattern.matcher(new TimeoutCharSequence(input, timeout));
    }

    /**
     * Replaces all occurrences of the regex in the input string.
     *
     * @param input the input string.
     * @param regex the regex pattern string.
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
     * @param input the input string.
     * @param regex the regex pattern string.
     * @param replacement the replacement string.
     * @param timeout the maximum duration for this operation.
     * @return the result with all matches replaced.
     * @throws RegexTimeoutException if the operation exceeds the given timeout.
     */
    public static String replaceAll(String input, String regex, String replacement, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        return matcher(pattern, input, timeout).replaceAll(replacement);
    }

    /**
     * A repetition quantifier: {@code +}, {@code *}, {@code ?} (unescaped — a backslash-escaped
     * {@code \+}/{@code \*}/{@code \?} is a literal character, not a quantifier), or a curly-brace
     * count ({@code {n}}, {@code {n,}}, {@code {n,m}}).
     */
    private static final String QUANTIFIER = "(?:(?<!\\\\)[+*?]|\\{\\d+(?:,\\d*)?})";

    private static final Pattern QUANTIFIER_FINDER = Pattern.compile(QUANTIFIER);

    /**
     * A group containing a quantifier (including {@code ?}, e.g. {@code (a?)}) that is itself
     * followed by another quantifier, e.g. {@code (a+)+}, {@code (a*)*}, {@code (a?){25}}. Nesting an
     * optional/unbounded quantifier inside a repeated group is the classic signature of catastrophic
     * backtracking (ReDoS), whether the outer repetition is unbounded ({@code +}/{@code *}) or a large
     * bounded count ({@code {25}}). Group 1 is the group body, group 2 the outer quantifier.
     */
    private static final Pattern NESTED_QUANTIFIER = Pattern.compile(
        "\\(([^()]*" + QUANTIFIER + "[^()]*)\\)\\s*(" + QUANTIFIER + ")"
    );

    /**
     * A group containing a top-level alternation ({@code |}) that is itself followed by a
     * quantifier, e.g. {@code (a|a)+}, {@code (a|ab)*}. Ambiguous alternation combined with
     * repetition is another classic catastrophic-backtracking shape, distinct from a nested
     * quantifier. Group 1 is the group body, group 2 the outer quantifier.
     */
    private static final Pattern ALTERNATION_WITH_REPETITION = Pattern.compile(
        "\\(([^()|]*\\|[^()]*)\\)\\s*(" + QUANTIFIER + ")"
    );

    /**
     * Largest outer repetition count of a group whose inner quantifiers are all bounded for which the
     * total backtracking stays polynomial with a small degree, e.g. {@code (\\d{1,3}\\.){3}}.
     */
    private static final int MAX_BOUNDED_NESTED_REPETITION = 5;

    /**
     * Total number of quantifiers that all the exempted repeated groups of one pattern may carry, since
     * each additional group multiplies the backtracking cost of the previous ones.
     */
    private static final int MAX_EXEMPT_QUANTIFIERS = 3;

    private static final Pattern BOUNDED_QUANTIFIER = Pattern.compile("\\?|\\{(\\d+)(?:,(\\d+))?}");

    /**
     * One atom of a flat group body: a character class, an escape or a single character, with its
     * optional quantifier. Group 1 is the atom, group 2 its quantifier.
     */
    private static final Pattern ATOM = Pattern.compile("(\\[(?:\\\\.|[^\\]])*]|\\\\.|[^\\\\\\[])(" + QUANTIFIER + ")?");

    private static final String REGEX_META_CHARACTERS = ".^$|?*+(){}[]\\";

    /**
     * Checks whether a user-supplied regex pattern is safe to execute against a database engine
     * (e.g. via Postgres {@code ~} or MySQL {@code REGEXP}), which offer no backtracking timeout of
     * their own.
     *
     * <p>
     * This is a heuristic, not a full ReDoS analysis: it rejects patterns that are too long, and
     * patterns containing a group with a nested quantifier (e.g. {@code (a+)+}, {@code (a?){25}}) or a
     * repeated ambiguous alternation (e.g. {@code (a|a)+}) — the two most common causes of
     * catastrophic backtracking. Other shapes (e.g. backreference-based ambiguity, or blowup spread
     * across multiple sibling groups) are not covered.
     * </p>
     *
     * <p>
     * A repeated group is still accepted when it cannot backtrack catastrophically: it is repeated at
     * most once ({@code (a+)?}), it is repeated a small bounded number of times around small bounded
     * quantifiers only ({@code (\\d{1,3}\\.){3}}), or, for nested quantifiers, each iteration starts or
     * ends with a literal separator that no quantified part of the group can match
     * ({@code ([a-z]+\\.)*}, {@code (-[a-z]+)*}). The first two exemptions share a small budget per pattern.
     * </p>
     *
     * @param pattern the user-supplied regex pattern.
     * @return {@code true} if the pattern is within the length limit and every nested quantifier or
     *         repeated alternation it contains is covered by one of the exemptions above.
     */
    public static boolean isSafeUserRegex(String pattern) {
        if (pattern == null || pattern.length() > MAX_USER_REGEX_LENGTH) {
            return false;
        }
        int[] budget = { MAX_EXEMPT_QUANTIFIERS };
        return !hasUnsafeRepetition(NESTED_QUANTIFIER, pattern, true, budget)
            && !hasUnsafeRepetition(ALTERNATION_WITH_REPETITION, pattern, false, budget);
    }

    private static boolean hasUnsafeRepetition(Pattern detector, String pattern, boolean nested, int[] budget) {
        Matcher matcher = detector.matcher(pattern);
        while (matcher.find()) {
            String body = matcher.group(1);
            if (body.startsWith("?:")) {
                body = body.substring(2);
            }
            if (nested && !QUANTIFIER_FINDER.matcher(body).find()) {
                continue;
            }
            if (nested && !body.contains("|") && hasSeparator(body)) {
                continue;
            }
            if (!isExemptRepetition(body, matcher.group(2), nested, budget)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExemptRepetition(String body, String outer, boolean nested, int[] budget) {
        int outerBound = upperBound(outer);
        boolean exempt = outerBound <= 1 || (nested && outerBound <= MAX_BOUNDED_NESTED_REPETITION && hasSmallBoundedQuantifiers(body));
        if (!exempt) {
            return false;
        }
        budget[0] -= Math.max(1, quantifiedAtoms(body));
        return budget[0] >= 0;
    }

    private static int upperBound(String quantifier) {
        Matcher matcher = BOUNDED_QUANTIFIER.matcher(quantifier);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        if (matcher.group(1) == null) {
            return 1;
        }
        String upper = matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
        return upper.length() > 4 ? Integer.MAX_VALUE : Integer.parseInt(upper);
    }

    private static boolean hasSmallBoundedQuantifiers(String body) {
        Matcher atoms = ATOM.matcher(body);
        while (atoms.find()) {
            String quantifier = atoms.group(2);
            if (quantifier != null && (!BOUNDED_QUANTIFIER.matcher(quantifier).matches() || upperBound(quantifier) > MAX_BOUNDED_NESTED_REPETITION)) {
                return false;
            }
        }
        return true;
    }

    private static int quantifiedAtoms(String body) {
        int count = 0;
        Matcher atoms = ATOM.matcher(body);
        while (atoms.find()) {
            if (atoms.group(2) != null) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasSeparator(String body) {
        List<String[]> atoms = new ArrayList<>();
        Matcher matcher = ATOM.matcher(body);
        while (matcher.find()) {
            atoms.add(new String[] { matcher.group(1), matcher.group(2) });
        }
        if (atoms.isEmpty()) {
            return false;
        }

        for (String[] candidate : List.of(atoms.getFirst(), atoms.getLast())) {
            String literal = literalOf(candidate);
            if (literal != null && atoms.stream().noneMatch(atom -> atom[1] != null && canMatch(atom[0], literal))) {
                return true;
            }
        }
        return false;
    }

    private static String literalOf(String[] atom) {
        if (atom[1] != null) {
            return null;
        }
        String text = atom[0];
        if (text.length() == 1 && REGEX_META_CHARACTERS.indexOf(text.charAt(0)) < 0) {
            return text;
        }
        if (text.length() == 2 && text.charAt(0) == '\\' && !Character.isLetterOrDigit(text.charAt(1))) {
            return text.substring(1);
        }
        return null;
    }

    // Case-insensitive and dot-all keep this conservative when the full pattern enables those flags inline.
    private static boolean canMatch(String atom, String literal) {
        try {
            return Pattern.compile(atom, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(literal).matches();
        } catch (PatternSyntaxException e) {
            return true;
        }
    }

    /**
     * Checks whether a user-supplied regex pattern compiles under Java's regex engine, which is the
     * only compile-time gate available before the pattern is handed to a database engine (e.g. via
     * Postgres {@code ~} or MySQL {@code REGEXP}). Java's dialect and a database's are close but not
     * identical, so a pattern accepted here may still be rejected by the engine, and vice versa.
     *
     * @param pattern the user-supplied regex pattern; must not be {@code null}.
     * @return empty if the pattern compiles, otherwise the syntax error description.
     */
    public static Optional<String> syntaxError(String pattern) {
        try {
            Pattern.compile(pattern);
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            return Optional.of(
                e.getIndex() >= 0
                    ? "%s near index %d".formatted(e.getDescription(), e.getIndex())
                    : e.getDescription()
            );
        }
    }

    /**
     * Exception thrown when a regex operation exceeds the configured timeout.
     */
    public static class RegexTimeoutException extends RuntimeException {
        public RegexTimeoutException(Duration timeout) {
            super(
                "Regex operation timed out after " + timeout.toMillis() + "ms. " +
                    "The pattern may be vulnerable to catastrophic backtracking (ReDoS)."
            );
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
