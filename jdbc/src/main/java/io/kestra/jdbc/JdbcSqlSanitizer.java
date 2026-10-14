package io.kestra.jdbc;

/**
 * Sanitizes a rendered SQL string so it is safe to use as a metric tag value.
 * <p>
 * Kestra's queries are already parameterized (jOOQ renders {@code ?} bind placeholders, not inlined
 * literal values), so the one thing left unbounded in a rendered query is free-form text a caller
 * controls: a variable-length {@code IN}/{@code NOT IN} list (one {@code ?} per element), a
 * dashboard-authored column alias, or a free-text {@code ORDER BY} column. This mirrors OpenTelemetry's
 * own semantic-convention recommendation for sanitizing {@code db.query.text}
 * (<a href="https://opentelemetry.io/docs/specs/semconv/db/sql/">SQL database client spans</a>):
 * "IN-clauses MAY be collapsed during sanitization, e.g. from {@code IN (?, ?, ?, ?)} to
 * {@code IN (?)}, as this can help with extremely long IN-clauses, and can help control cardinality."
 * <p>
 * {@link #sanitize(String)} applies three independent passes: {@link #collapseInLists}, then
 * {@link #redactQuotedIdentifiers}, then {@link #redactOrderByColumns}. The order between them does
 * not matter and is not a contract to preserve carefully — each pass's output never contains a token
 * another pass searches for ({@code (?)}, a quoted {@code ?}, or a bare {@code ?} respectively), and
 * every pass skips over quoted regions while searching for its own keyword, so an alias whose text
 * happens to contain {@code IN (} or {@code ORDER BY} cannot cause cross-pass interference.
 * <p>
 * Every scan fails open rather than throwing: an unterminated quote stops that pass and copies the
 * remainder of the string through unprocessed, and a value list/sort list that turns out to be
 * malformed (e.g. an unbalanced parenthesis) is simply left as-is while the scan continues normally
 * from the next character — later, well-formed occurrences elsewhere in the same string are still
 * found and sanitized. Either way, a slightly less sanitized tag value is a far smaller problem than
 * losing this query's metric entirely.
 */
public final class JdbcSqlSanitizer {
    private static final char SINGLE_QUOTE = '\'';
    private static final String IN_KEYWORD = "IN";
    private static final String SELECT_KEYWORD = "SELECT";
    private static final String ORDER_KEYWORD = "ORDER";
    private static final String BY_KEYWORD = "BY";

    /** Keywords that can follow an {@code ORDER BY} sort list at parenthesis depth 0. */
    private static final String[] SORT_LIST_BOUNDARY_KEYWORDS = { "LIMIT", "OFFSET", "FETCH", "FOR" };

    private JdbcSqlSanitizer() {
        // utility class
    }

    /**
     * Sanitizes {@code sql} so its cardinality as a metric tag value is bounded, regardless of how
     * many elements a caller-supplied list has or what text a caller-supplied alias/column contains.
     *
     * @param sql the rendered SQL, may be {@code null}
     * @return the sanitized SQL, or {@code null}/empty unchanged
     */
    public static String sanitize(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String result = collapseInLists(sql);
        result = redactQuotedIdentifiers(result);
        return redactOrderByColumns(result);
    }

    /**
     * Collapses every {@code IN}/{@code NOT IN} value list to a single element, e.g.
     * {@code IN (?, ?, ?, ?)} becomes {@code IN (?)}, so a query's tag no longer varies with how many
     * values a caller passed to an {@code In}/{@code NotIn} filter. A subquery form,
     * {@code IN (SELECT ...)}, is left untouched — Kestra never renders this today, but preserving it
     * costs one keyword check and keeps the tag useful if that ever changes.
     */
    static String collapseInLists(String sql) {
        int len = sql.length();
        StringBuilder result = new StringBuilder(len);
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    result.append(sql, i, len);
                    return result.toString();
                }
                result.append(sql, i, end);
                i = end;
                continue;
            }

            if (isKeywordAt(sql, i, IN_KEYWORD)) {
                int open = skipWhitespace(sql, i + IN_KEYWORD.length());
                int close = open < len && sql.charAt(open) == '(' ? endOfParenthesis(sql, open) : -1;
                if (close > open && !isSubqueryIn(sql, open)) {
                    result.append(sql, i, open + 1).append("?)");
                    i = close + 1;
                    continue;
                }
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Redacts the content of every double-quoted or backtick-quoted identifier to a single
     * {@code ?}, e.g. {@code AS "dashboard column"} becomes {@code AS "?"}. This bounds every SQL
     * alias uniformly, both the ones driven by user input (a dashboard column key) and the small set
     * of fixed aliases already in the codebase ({@code cte}, {@code ft}, {@code metric_value}, ...)
     * — deliberately not distinguishing between them, since an allow-list of "safe" aliases would (a)
     * need to be kept in sync with every {@code .as(...)} call site and (b) be a correctness trap: a
     * dashboard column literally named {@code cte} would slip through it unredacted. Single-quoted
     * text (string value literals, never identifiers) is left untouched.
     */
    static String redactQuotedIdentifiers(String sql) {
        int len = sql.length();
        StringBuilder result = new StringBuilder(len);
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            if (c == SINGLE_QUOTE) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    result.append(sql, i, len);
                    return result.toString();
                }
                result.append(sql, i, end);
                i = end;
                continue;
            }

            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    result.append(sql, i, len);
                    return result.toString();
                }
                result.append(c).append('?').append(c);
                i = end;
                continue;
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Redacts the sort-list of an {@code ORDER BY} clause to a single {@code ?}, e.g.
     * {@code ORDER BY dashboard_sort_column ASC} becomes {@code ORDER BY ?}. Unlike the alias
     * redaction above, a plain (unquoted) column reference passed to {@code field(String)} renders
     * with no quoting at all, so it needs its own keyword-based scan rather than being caught by
     * {@link #redactQuotedIdentifiers}.
     */
    static String redactOrderByColumns(String sql) {
        int len = sql.length();
        StringBuilder result = new StringBuilder(len);
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    result.append(sql, i, len);
                    return result.toString();
                }
                result.append(sql, i, end);
                i = end;
                continue;
            }

            if (isKeywordAt(sql, i, ORDER_KEYWORD)) {
                int by = skipWhitespace(sql, i + ORDER_KEYWORD.length());
                if (isKeywordAt(sql, by, BY_KEYWORD)) {
                    int afterBy = by + BY_KEYWORD.length();
                    int start = skipWhitespace(sql, afterBy);
                    int end = endOfSortList(sql, start);
                    if (end > start) {
                        result.append(sql, i, afterBy).append(" ?");
                        i = end;
                        continue;
                    }
                }
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    // true when the parenthesized body immediately following IN/NOT IN is a subquery rather than a
    // value list
    private static boolean isSubqueryIn(String sql, int open) {
        int contentStart = skipWhitespace(sql, open + 1);
        return isKeywordAt(sql, contentStart, SELECT_KEYWORD);
    }

    // finds where an ORDER BY sort-list ends: the first parenthesis-depth-0 occurrence of a boundary
    // keyword (LIMIT/OFFSET/FETCH/FOR) or an unbalanced ')' (a derived table or window function's
    // enclosing parenthesis, which must not be consumed), trimming trailing whitespace off the result
    // so the original formatting before that boundary is preserved. The boundary check only applies
    // once at least one character of the sort list has been scanned (i > start): otherwise a column
    // literally named "offset" would be mistaken for the OFFSET clause keyword at position 0 and
    // yield an empty (unredacted) span.
    // Known, accepted limitation: this trades away detecting a *malformed, empty* sort list (e.g. a
    // hand-written "ORDER BY LIMIT 10" with no column at all) — that case is indistinguishable from a
    // real column literally named "limit", so the scan continues past the boundary keyword and
    // redacts the clause that follows it too. jOOQ itself never renders an empty ORDER BY (it omits
    // the clause entirely when no sort field is set), and this codebase has no hand-written ORDER BY
    // SQL, so this is not reachable today; flagged here rather than silently relied upon.
    private static int endOfSortList(String sql, int start) {
        int len = sql.length();
        int depth = 0;
        int i = start;

        while (i < len) {
            char c = sql.charAt(i);

            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    i = len;
                    break;
                }
                i = end;
                continue;
            }

            if (c == '(') {
                depth++;
                i++;
                continue;
            }

            if (c == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
                i++;
                continue;
            }

            if (depth == 0 && i > start && isAnyKeywordAt(sql, i, SORT_LIST_BOUNDARY_KEYWORDS)) {
                break;
            }

            i++;
        }

        while (i > start && Character.isWhitespace(sql.charAt(i - 1))) {
            i--;
        }

        return i;
    }

    private static boolean isAnyKeywordAt(String sql, int index, String[] keywords) {
        for (String keyword : keywords) {
            if (isKeywordAt(sql, index, keyword)) {
                return true;
            }
        }

        return false;
    }

    // walks past a quoted span (single/double/backtick, matching whatever quote character opens it
    // at "quoteIndex"), handling the doubled-quote escape convention (e.g. "a""b" for a literal quote
    // inside an identifier); returns the index right after the closing quote, or -1 if unterminated
    private static int endOfQuoted(String sql, int quoteIndex) {
        char quote = sql.charAt(quoteIndex);
        int len = sql.length();
        int j = quoteIndex + 1;

        while (j < len) {
            if (sql.charAt(j) == quote) {
                if (j + 1 < len && sql.charAt(j + 1) == quote) {
                    j += 2;
                    continue;
                }
                return j + 1;
            }
            j++;
        }

        return -1;
    }

    // finds the parenthesis matching the '(' at "openIndex" by depth counting that itself skips
    // quoted spans (so a ')' or ',' inside a string literal inside the parens cannot desync the
    // depth count); returns the index of the matching ')', or -1 if unbalanced
    private static int endOfParenthesis(String sql, int openIndex) {
        int len = sql.length();
        int depth = 0;
        int i = openIndex;

        while (i < len) {
            char c = sql.charAt(i);

            if (isQuote(c)) {
                int end = endOfQuoted(sql, i);
                if (end == -1) {
                    return -1;
                }
                i = end;
                continue;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }

            i++;
        }

        return -1;
    }

    private static int skipWhitespace(String sql, int index) {
        int i = index;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }

        return i;
    }

    // matches "keyword" at "index" as a whole word: case-insensitive, and not a substring of a
    // longer identifier (neither neighbour is a letter/digit/underscore)
    private static boolean isKeywordAt(String sql, int index, String keyword) {
        int end = index + keyword.length();
        return index >= 0 && end <= sql.length()
            && sql.regionMatches(true, index, keyword, 0, keyword.length())
            && (index == 0 || !isIdentifierChar(sql.charAt(index - 1)))
            && (end == sql.length() || !isIdentifierChar(sql.charAt(end)));
    }

    private static boolean isQuote(char c) {
        return c == SINGLE_QUOTE || c == '"' || c == '`';
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
