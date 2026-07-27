package io.kestra.core.utils;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.pebbletemplates.pebble.lexer.Syntax;

/**
 * Utility for Pebble template delimiter detection.
 */
public final class PebbleUtil {

    private static final Syntax.Builder DEFAULT_SYNTAX = new Syntax.Builder();

    private static final List<String> OPENING_BLOCK_DELIMITERS = List.of(
        DEFAULT_SYNTAX.getPrintOpenDelimiter(),
        DEFAULT_SYNTAX.getExecuteOpenDelimiter()
    );

    private static final List<String> CLOSING_BLOCK_DELIMITERS = List.of(
        DEFAULT_SYNTAX.getPrintCloseDelimiter(),
        DEFAULT_SYNTAX.getExecuteCloseDelimiter()
    );

    /** Matches a full Pebble block ({@code {{ ... }}} or {@code {% ... %}}), built from the delimiter pairs above. */
    private static final Pattern BLOCK_PATTERN = Pattern.compile(
        IntStream.range(0, OPENING_BLOCK_DELIMITERS.size())
            .mapToObj(i -> Pattern.quote(OPENING_BLOCK_DELIMITERS.get(i)) + ".*?" + Pattern.quote(CLOSING_BLOCK_DELIMITERS.get(i)))
            .collect(Collectors.joining("|")),
        Pattern.DOTALL
    );

    private PebbleUtil() {
    }

    /**
     * Returns the opening block delimiters for Pebble expressions ({@code {{} and {@code {%}).
     */
    public static List<String> openingBlockDelimiters() {
        return OPENING_BLOCK_DELIMITERS;
    }

    /**
     * Returns the closing block delimiters for Pebble expressions ({@code }}} and {@code %}).
     */
    public static List<String> closingBlockDelimiters() {
        return CLOSING_BLOCK_DELIMITERS;
    }

    /**
     * Returns {@code true} if the given string contains any opening Pebble block delimiter.
     */
    public static boolean containsOpeningBlockDelimiter(String value) {
        return OPENING_BLOCK_DELIMITERS.stream().anyMatch(value::contains);
    }

    /**
     * Returns {@code true} if the given string starts with any opening Pebble block delimiter.
     */
    public static boolean startsWithOpeningBlockDelimiter(String value) {
        return OPENING_BLOCK_DELIMITERS.stream().anyMatch(value::startsWith);
    }

    /**
     * Returns {@code true} if the given string ends with any closing Pebble block delimiter.
     */
    public static boolean endsWithClosingBlockDelimiter(String value) {
        return CLOSING_BLOCK_DELIMITERS.stream().anyMatch(value::endsWith);
    }

    /**
     * Applies {@code transform} to the content of each Pebble block ({@code {{ ... }}} or {@code {% ... %}}) found in
     * {@code value}, leaving any text outside a block untouched.
     */
    public static String replaceInBlock(String value, UnaryOperator<String> transform) {
        Matcher matcher = BLOCK_PATTERN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(transform.apply(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
