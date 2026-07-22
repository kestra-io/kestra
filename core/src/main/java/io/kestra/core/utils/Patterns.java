package io.kestra.core.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Caches compiled {@link Pattern}s keyed by their source string, for regexes evaluated repeatedly —
 * e.g. policy rule conditions matched on every flow parse — where {@link String#matches} would
 * recompile the pattern on each call. Unbounded by design: callers pass admin-authored expressions
 * of tiny cardinality, never user input.
 */
public final class Patterns {

    private static final ConcurrentHashMap<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private Patterns() {
    }

    /**
     * Returns the compiled {@link Pattern} for the given regex, compiling it on first use.
     *
     * @throws PatternSyntaxException when the regex is invalid
     */
    public static Pattern of(String regex) {
        return CACHE.computeIfAbsent(regex, Pattern::compile);
    }
}
