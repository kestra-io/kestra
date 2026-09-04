package io.kestra.core.models.dashboards.filters;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TypeConverter;

/**
 * Rewrites the filters set on a duration field so that their values are expressed in the unit the store persists.
 * <p>
 * A dashboard writes a duration the way the rest of Kestra does — an ISO-8601 duration such as {@code PT1S}, or a
 * plain number of seconds — while the stores keep a number: milliseconds on JDBC, seconds on ElasticSearch. The
 * authored value can therefore not be handed to the store as-is: it would be compared to a numeric column and fail in
 * the database. Every store normalizes its duration filters through {@link #normalize} first, and
 * {@link #violations} reports the same rules at save time.
 */
public final class DurationFilters {
    private DurationFilters() {
    }

    /**
     * @param durationFields the duration-typed fields, as declared by {@link io.kestra.plugin.core.dashboard.data.IData#durationFields()}
     * @param toStoreUnit    converts a duration to the number the store holds for it
     * @return the filters, each one on a duration field replaced by an equivalent filter carrying a store-unit value
     * @throws InvalidQueryFiltersException when a value is not a duration, or the filter type cannot apply to one
     */
    public static <F extends Enum<F>> List<AbstractFilter<F>> normalize(List<AbstractFilter<F>> filters, Set<? extends Enum<?>> durationFields, Function<Duration, Number> toStoreUnit) {
        if (ListUtils.isEmpty(filters) || durationFields.isEmpty()) {
            return filters;
        }

        return filters.stream().map(filter -> normalizeFilter(filter, durationFields, toStoreUnit)).toList();
    }

    /**
     * Collects the reasons why the duration filters of a chart cannot be queried, so that they are reported when the
     * dashboard is saved rather than when the chart is rendered.
     *
     * @return one message per invalid filter, empty when they are all valid
     */
    public static List<String> violations(List<? extends AbstractFilter<?>> filters, Set<? extends Enum<?>> durationFields) {
        if (ListUtils.isEmpty(filters) || durationFields.isEmpty()) {
            return List.of();
        }

        List<String> violations = new ArrayList<>();
        for (AbstractFilter<?> filter : filters) {
            if (filter instanceof Or<?> or) {
                violations.addAll(violations(or.getValues(), durationFields));
            } else if (durationFields.contains(filter.getField())) {
                // the unit is irrelevant here: a filter is valid when every one of its values is a duration
                try {
                    rewrite(filter, Duration::toMillis);
                } catch (InvalidQueryFiltersException e) {
                    violations.add(e.getMessage());
                }
            }
        }

        return violations;
    }

    private static <F extends Enum<F>> AbstractFilter<F> normalizeFilter(AbstractFilter<F> filter, Set<? extends Enum<?>> durationFields, Function<Duration, Number> toStoreUnit) {
        if (filter instanceof Or<F> or) {
            return Or.<F> builder()
                .field(or.getField())
                .key(or.getKey())
                .values(normalize(or.getValues(), durationFields, toStoreUnit))
                .build();
        }

        if (!durationFields.contains(filter.getField())) {
            return filter;
        }

        return rewrite(filter, toStoreUnit);
    }

    // the field of a wildcard filter can only be the enum the caller matched it against
    @SuppressWarnings("unchecked")
    private static <F extends Enum<F>> AbstractFilter<F> rewrite(AbstractFilter<?> filter, Function<Duration, Number> toStoreUnit) {
        F field = (F) filter.getField();
        String key = filter.getKey();

        return switch (filter) {
            case EqualTo<?> f -> EqualTo.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case NotEqualTo<?> f -> NotEqualTo.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case GreaterThan<?> f -> GreaterThan.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case GreaterThanOrEqualTo<?> f -> GreaterThanOrEqualTo.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case LessThan<?> f -> LessThan.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case LessThanOrEqualTo<?> f -> LessThanOrEqualTo.<F> builder().field(field).key(key).value(value(f.getValue(), toStoreUnit)).build();
            case In<?> f -> In.<F> builder().field(field).key(key).values(values(f.getValues(), toStoreUnit)).build();
            case NotIn<?> f -> NotIn.<F> builder().field(field).key(key).values(values(f.getValues(), toStoreUnit)).build();
            case IsNull<?> f -> (AbstractFilter<F>) f;
            case IsNotNull<?> f -> (AbstractFilter<F>) f;
            default -> throw new InvalidQueryFiltersException(
                "filter type `%s` cannot be used on the duration field `%s`.".formatted(filter.getType(), filter.getField())
            );
        };
    }

    private static Duration toDuration(Object value) {
        if (value == null) {
            throw new InvalidQueryFiltersException("a duration filter requires a value.");
        }

        try {
            // TypeConverter parses ISO-8601 only, a plain number is the number of seconds a duration is authored as elsewhere
            return value instanceof Number seconds ? ofSeconds(seconds) : TypeConverter.toDuration(value);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new InvalidQueryFiltersException("`%s` is not a valid duration, use an ISO-8601 duration such as `PT1S` or a number of seconds.".formatted(value), e);
        }
    }

    private static Duration ofSeconds(Number seconds) {
        BigDecimal value = new BigDecimal(seconds.toString());

        return Duration.ofSeconds(value.longValue(), value.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    private static Number value(Object value, Function<Duration, Number> toStoreUnit) {
        return toStoreUnit.apply(toDuration(value));
    }

    private static List<Object> values(List<Object> values, Function<Duration, Number> toStoreUnit) {
        return values.stream().map(value -> (Object) value(value, toStoreUnit)).toList();
    }
}
