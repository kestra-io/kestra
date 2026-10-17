package io.kestra.core.models;

/**
 * A single occurrence of a Source Search match within a flow's source.
 *
 * @param line 1-based line number.
 * @param column 0-based offset of the match start within its line, used to disambiguate multiple occurrences on the same line.
 * @param snippet the line text with the matched span wrapped in {@code [mark]}/{@code [/mark]}.
 */
public record SourceMatch(int line, int column, String snippet) {
}
