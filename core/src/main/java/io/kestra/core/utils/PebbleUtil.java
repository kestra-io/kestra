package io.kestra.core.utils;

import io.pebbletemplates.pebble.lexer.Syntax;

import java.util.List;

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

    private PebbleUtil() {}

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
}
