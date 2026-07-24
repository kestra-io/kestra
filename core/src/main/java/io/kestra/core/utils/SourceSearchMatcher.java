package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.kestra.core.exceptions.InvalidSourceSearchQueryException;
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.flows.SourceSearchScope;

public final class SourceSearchMatcher {

    private static final int MAX_MATCHES_PER_SOURCE = 500;
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[A-Za-z_][\\w-]*:.*");

    private SourceSearchMatcher() {
    }

    public static Pattern toPattern(String query, boolean caseSensitive, boolean wholeWord, boolean regex) {
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
