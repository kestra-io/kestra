package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.kestra.core.exceptions.InvalidSourceSearchQueryException;
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.flows.SourceSearchScope;

public final class SourceSearchMatcher {

    /**
     * Maximum number of flows a single source search loads to match in memory. Whole-word, regex and
     * scope matching cannot be delegated to the datastore, so every repository implementation has to
     * bound the candidate set it pulls back before matching.
     */
    public static final int MAX_SOURCE_SEARCH_CANDIDATES = 1000;

    private static final int MAX_MATCHES_PER_SOURCE = 500;
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[A-Za-z_][\\w-]*:.*");

    private SourceSearchMatcher() {
    }

    /**
     * Rejects a user-supplied regular expression that is too long or prone to catastrophic
     * backtracking (ReDoS). Literal queries are always safe as they get quoted before compilation.
     *
     * @throws InvalidSourceSearchQueryException if the query is a regex and is not safe to evaluate.
     */
    public static void ensureSafeQuery(String query, boolean regex) {
        if (regex && !RegexUtils.isSafeUserRegex(query)) {
            throw new InvalidSourceSearchQueryException(
                "The regular expression is rejected as unsafe. It must be at most %d characters long and must not nest a quantifier or repeat an alternation, as both can cause catastrophic backtracking. Please simplify the pattern."
                    .formatted(RegexUtils.MAX_USER_REGEX_LENGTH)
            );
        }
    }

    /**
     * Rejects a replacement string that Java's regex engine could not expand against the given
     * pattern, i.e. one holding a trailing backslash, a malformed group reference, or a reference to a
     * capturing group the pattern does not declare. Literal replacements are always valid as they get
     * quoted before expansion.
     *
     * @throws InvalidSourceSearchQueryException if the replacement cannot be expanded.
     */
    public static void ensureValidReplacement(Pattern pattern, String replacement, boolean regex) {
        if (!regex || replacement == null) {
            return;
        }

        int groupCount = pattern.matcher("").groupCount();
        Map<String, Integer> namedGroups = pattern.namedGroups();

        for (int i = 0; i < replacement.length(); i++) {
            char c = replacement.charAt(i);

            if (c == '\\') {
                if (i + 1 == replacement.length()) {
                    throw new InvalidSourceSearchQueryException(
                        "The replacement ends with a dangling backslash. Escape it as '\\\\' to insert a literal backslash."
                    );
                }
                i++;
            } else if (c == '$') {
                i = validateGroupReference(replacement, i, groupCount, namedGroups);
            }
        }
    }

    private static int validateGroupReference(String replacement, int dollarIndex, int groupCount, Map<String, Integer> namedGroups) {
        if (dollarIndex + 1 == replacement.length()) {
            throw new InvalidSourceSearchQueryException(
                "The replacement ends with a dangling '$'. Escape it as '\\$' to insert a literal dollar sign."
            );
        }

        char next = replacement.charAt(dollarIndex + 1);
        if (next == '{') {
            int closing = replacement.indexOf('}', dollarIndex + 2);
            if (closing < 0) {
                throw new InvalidSourceSearchQueryException(
                    "The replacement contains a named group reference with no closing '}'."
                );
            }
            String name = replacement.substring(dollarIndex + 2, closing);
            if (!namedGroups.containsKey(name)) {
                throw new InvalidSourceSearchQueryException(
                    "The replacement refers to the named group '%s', which the search pattern does not declare.".formatted(name)
                );
            }
            return closing;
        }

        if (!Character.isDigit(next)) {
            throw new InvalidSourceSearchQueryException(
                "The replacement contains an illegal group reference '$%c'. Use '$1', '${name}', or escape it as '\\$'.".formatted(next)
            );
        }
        if (next - '0' > groupCount) {
            throw new InvalidSourceSearchQueryException(
                "The replacement refers to the capturing group %c, but the search pattern declares only %d.".formatted(next, groupCount)
            );
        }

        return dollarIndex + 1;
    }

    public static Pattern toPattern(String query, boolean caseSensitive, boolean wholeWord, boolean regex) {
        ensureSafeQuery(query, regex);

        String base = regex ? query : Pattern.quote(query);
        String withBoundaries = wholeWord ? "\\b(?:" + base + ")\\b" : base;
        int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        try {
            return Pattern.compile(withBoundaries, flags);
        } catch (PatternSyntaxException e) {
            String description = e.getIndex() >= 0
                ? "%s near index %d".formatted(e.getDescription(), e.getIndex())
                : e.getDescription();
            throw new InvalidSourceSearchQueryException(description);
        }
    }

    public static List<SourceMatch> findMatches(String source, String query, boolean caseSensitive, boolean wholeWord, boolean regex, SourceSearchScope scope) {
        List<SourceMatch> matches = findMatches(source, query, caseSensitive, wholeWord, regex);
        if (scope == null || scope == SourceSearchScope.ALL || matches.isEmpty()) {
            return matches;
        }

        int[] range = topLevelBlockLineRange(source, scope.name().toLowerCase(Locale.ROOT));
        if (range == null) {
            return List.of();
        }

        return matches.stream().filter(match -> match.line() >= range[0] && match.line() <= range[1]).toList();
    }

    public static List<SourceMatch> findMatches(String source, String query, boolean caseSensitive, boolean wholeWord, boolean regex) {
        if (source == null || query == null || query.isEmpty()) {
            return List.of();
        }

        Pattern pattern = toPattern(query, caseSensitive, wholeWord, regex);
        Matcher matcher = RegexUtils.matcher(pattern, source);
        List<int[]> lineBounds = lineBounds(source);

        List<SourceMatch> matches = new ArrayList<>();
        int lineIndex = 0;
        int searchFrom = 0;
        while (searchFrom <= source.length() && matches.size() < MAX_MATCHES_PER_SOURCE) {
            if (!matcher.find(searchFrom)) {
                break;
            }

            int start = matcher.start();
            int end = matcher.end();

            while (lineIndex < lineBounds.size() - 1 && lineBounds.get(lineIndex)[1] < start) {
                lineIndex++;
            }
            int[] bounds = lineBounds.get(lineIndex);
            int highlightEnd = Math.max(start, Math.min(end, bounds[1]));

            String snippet = source.substring(bounds[0], start)
                + "[mark]" + source.substring(start, highlightEnd) + "[/mark]"
                + source.substring(highlightEnd, bounds[1]);
            matches.add(new SourceMatch(lineIndex + 1, start - bounds[0], snippet));

            searchFrom = end == start ? end + 1 : end;
        }

        return matches;
    }

    public static String replaceWithinScope(String source, Pattern pattern, String replacement, SourceSearchScope scope) {
        if (scope == null || scope == SourceSearchScope.ALL) {
            return RegexUtils.matcher(pattern, source).replaceAll(replacement);
        }

        int[] range = topLevelBlockLineRange(source, scope.name().toLowerCase(Locale.ROOT));
        if (range == null) {
            return source;
        }

        List<int[]> lines = lineBounds(source);
        int startOffset = lines.get(range[0] - 1)[0];
        int endOffset = lines.get(Math.min(range[1], lines.size()) - 1)[1];

        String before = source.substring(0, startOffset);
        String scoped = source.substring(startOffset, endOffset);
        String after = source.substring(endOffset);

        return before + RegexUtils.matcher(pattern, scoped).replaceAll(replacement) + after;
    }

    public static String extractLine(String source, int lineNumber) {
        List<int[]> bounds = lineBounds(source);
        int index = lineNumber - 1;
        if (index < 0 || index >= bounds.size()) {
            return "";
        }
        int[] b = bounds.get(index);
        return source.substring(b[0], b[1]);
    }

    private static int[] topLevelBlockLineRange(String source, String key) {
        String[] lines = source.split("\n", -1);

        int start = -1;
        int end = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isTopLevelKey = TOP_LEVEL_KEY.matcher(line).matches();

            if (start == -1) {
                if (isTopLevelKey && line.startsWith(key + ":")) {
                    start = i + 1;
                }
            } else if (isTopLevelKey) {
                end = i;
                break;
            }
        }

        return start == -1 ? null : new int[]{start, end};
    }

    private static List<int[]> lineBounds(String source) {
        List<int[]> bounds = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                bounds.add(new int[]{start, i});
                start = i + 1;
            }
        }
        bounds.add(new int[]{start, source.length()});
        return bounds;
    }
}
