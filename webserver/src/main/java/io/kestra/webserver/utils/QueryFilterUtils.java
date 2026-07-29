package io.kestra.webserver.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.utils.DateUtils;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryFilterUtils {

    /**
     * Number of days to look back by default when a query provides no lower time bound. This guards
     * against flooding the database with an unbounded scan.
     */
    private static final int DEFAULT_LOOKBACK_DAYS = 8;

    public static void validateTimeline(List<QueryFilter> filters) {
        DateUtils.validateTimeline(filters);
    }

    /**
     * Resolves a relative ISO-8601 duration value (e.g. {@code PT5M}, {@code P7D}) on a date-typed leaf
     * into an absolute instant. Past-oriented fields resolve to {@code now().minus(duration)}, future-oriented
     * fields (see {@link QueryFilter.Field#dateOrientation()}) to {@code now().plus(duration)}.
     * Absolute values and non-date fields are returned unchanged.
     */
    private static QueryFilter resolveRelativeDate(QueryFilter leaf, ZonedDateTime now) {
        if (leaf == null || leaf.field() == null || !leaf.field().isDateField() || leaf.value() == null
            || leaf.value() instanceof ZonedDateTime) {
            return leaf;
        }
        String raw = leaf.value().toString();
        Duration duration = tryParseDuration(raw);
        if (duration == null) {
            // Not a relative duration — it must be a valid absolute instant, otherwise reject the input.
            requireParsableInstant(leaf.field(), raw);
            return leaf;
        }
        ZonedDateTime resolved = leaf.field().dateOrientation() == QueryFilter.DateOrientation.FUTURE
            ? now.plus(duration) : now.minus(duration);
        return QueryFilter.builder()
            .field(leaf.field())
            .operation(leaf.operation())
            .value(resolved.toString())
            .build();
    }

    private static void requireParsableInstant(QueryFilter.Field field, String raw) {
        try {
            ZonedDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date or duration value for " + (field != null ? field.value() : "date") + ": " + raw);
        }
    }

    private static Duration tryParseDuration(String value) {
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Resolves a raw date-field value to an absolute {@link ZonedDateTime}. A relative ISO-8601 duration is
     * resolved against {@code now} using the field's orientation; anything else is parsed as an absolute instant.
     */
    public static ZonedDateTime resolveDateValue(QueryFilter.Field field, Object value, ZonedDateTime now) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZonedDateTime zdt) {
            return zdt;
        }
        Duration duration = tryParseDuration(value.toString());
        if (duration != null) {
            return field != null && field.dateOrientation() == QueryFilter.DateOrientation.FUTURE
                ? now.plus(duration) : now.minus(duration);
        }
        requireParsableInstant(field, value.toString());
        return ZonedDateTime.parse(value.toString());
    }

    /**
     * Recursively applies {@code leafMapper} to every leaf in the filter tree, rebuilding any
     * intermediate nodes so that leaves nested inside conditional groups are rewritten just like
     * top-level leaves.
     */
    private static QueryFilter mapLeavesRecursively(QueryFilter filter, UnaryOperator<QueryFilter> leafMapper) {
        if (filter.isNode()) {
            return QueryFilter.builder()
                .logical(filter.logical())
                .children(filter.children().stream().map(c -> mapLeavesRecursively(c, leafMapper)).toList())
                .build();
        }
        return leafMapper.apply(filter);
    }

    /**
     * Returns {@code true} if any leaf in the filter tree (including inside nested nodes) satisfies
     * {@code predicate}.
     */
    private static boolean anyLeafMatches(List<QueryFilter> filters, Predicate<QueryFilter> predicate) {
        return filters.stream().anyMatch(f -> f.isNode() ? anyLeafMatches(f.children(), predicate) : predicate.test(f));
    }

    /**
     * Resolves any relative ISO-8601 duration on a date-typed field into an absolute instant, computed
     * against a single {@code now()} for the whole filter tree. Absolute date values pass through untouched.
     */
    public static List<QueryFilter> resolveRelativeDateFilters(List<QueryFilter> filters) {
        if (filters == null) {
            return List.of();
        }
        ZonedDateTime now = ZonedDateTime.now();
        return filters.stream()
            .map(filter -> mapLeavesRecursively(filter, leaf -> resolveRelativeDate(leaf, now)))
            .toList();
    }

    /** Applies the default window on {@link QueryFilter.Field#START_DATE} — the bound for execution-like resources. */
    public static List<QueryFilter> applyDefaultWindow(List<QueryFilter> filters) {
        return applyDefaultWindow(filters, QueryFilter.Field.START_DATE);
    }

    /**
     * When the query carries no lower bound on {@code boundedField}, injects a default {@code -8 days} boundary
     * to protect the database from an unbounded scan. Callers pass the date field their resource is actually
     * windowed on: {@link QueryFilter.Field#START_DATE} for executions, {@link QueryFilter.Field#DATE} for
     * event-like resources such as logs.
     * <p>
     * A filter already present on that field counts as a bound whatever its operation, so an upper-bound-only
     * query ({@code date <= X}) is left untouched rather than being silently ANDed with an unrelated window.
     * <p>
     * Relative durations are already resolved to absolute instants upstream by
     * {@code QueryFilterFormatBinder}, so this method only deals with absolute dates.
     */
    public static List<QueryFilter> applyDefaultWindow(List<QueryFilter> filters, QueryFilter.Field boundedField) {
        QueryFilter.Field target = boundedField == null ? QueryFilter.Field.START_DATE : boundedField;
        if (!target.isDateField()) {
            throw new IllegalArgumentException("The default window must target a date field but was " + target);
        }
        List<QueryFilter> resolved = filters == null
            ? new java.util.ArrayList<>()
            : new java.util.ArrayList<>(filters);

        if (!anyLeafMatches(resolved, f -> f.field() == target)) {
            resolved.add(QueryFilter.builder()
                .field(target)
                .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                .value(ZonedDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS).toString())
                .build());
        }

        validateTimeline(resolved);
        return resolved;
    }
}
