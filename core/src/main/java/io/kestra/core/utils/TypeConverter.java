package io.kestra.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.kestra.core.exceptions.TypeConversionException;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Utility methods for converting raw values (typically {@link Object} or {@link String}) into typed values.
 *
 * <p>
 * General contract for all methods:
 * <ul>
 * <li>{@code null} in, {@code null} out.</li>
 * <li>A value already of the target type is returned as-is (passthrough).</li>
 * <li>Any other value is strictly parsed from its {@code toString()} representation — no trimming,
 * no locale handling, no lenient formats.</li>
 * <li>Conversion failures throw {@link TypeConversionException} (an {@link IllegalArgumentException})
 * carrying the original parse failure as cause.</li>
 * </ul>
 */
public final class TypeConverter {

    /**
     * Converts the given value to a {@link Boolean}.
     *
     * <p>
     * Uses {@link Boolean#parseBoolean(String)} semantics: only the literal {@code "true"}
     * (case-insensitive) is {@code true}; everything else — including {@code "1"} and {@code "yes"} —
     * is {@code false}. Do NOT use for flow-condition truthiness; use {@link TruthUtils} instead.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     */
    public static Boolean toBoolean(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            default -> Boolean.parseBoolean(value.toString());
        };
    }

    /**
     * Converts the given value to an {@link Integer}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as an integer.
     */
    public static Integer toInteger(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> i;
            default -> convert(value, Integer.class, v -> Integer.valueOf(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link Long}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a long.
     */
    public static Long toLong(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Long l -> l;
            default -> convert(value, Long.class, v -> Long.valueOf(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link Float}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a float.
     */
    public static Float toFloat(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Float f -> f;
            default -> convert(value, Float.class, v -> Float.valueOf(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link Double}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a double.
     */
    public static Double toDouble(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Double d -> d;
            default -> convert(value, Double.class, v -> Double.valueOf(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link Duration} using strict ISO-8601 parsing (e.g. {@code "PT1H"}).
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a duration.
     */
    public static Duration toDuration(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Duration d -> d;
            default -> convert(value, Duration.class, v -> Duration.parse(v.toString()));
        };
    }

    /**
     * Converts the given value to an {@link Instant} using strict ISO-8601 instant parsing
     * (e.g. {@code "2023-05-02T01:02:03Z"}). For lenient multi-format fallback parsing, use {@link DateUtils}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as an instant.
     */
    public static Instant toInstant(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            default -> convert(value, Instant.class, v -> Instant.parse(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link LocalDate} using strict ISO-8601 date parsing (e.g. {@code "2023-05-02"}).
     * For lenient multi-format fallback parsing, use {@link DateUtils}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a date.
     */
    public static LocalDate toLocalDate(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case LocalDate d -> d;
            default -> convert(value, LocalDate.class, v -> LocalDate.parse(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link LocalTime} using strict ISO-8601 time parsing (e.g. {@code "01:02:03"}).
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a time.
     */
    public static LocalTime toLocalTime(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case LocalTime t -> t;
            default -> convert(value, LocalTime.class, v -> LocalTime.parse(v.toString()));
        };
    }

    /**
     * Converts the given value to a {@link ZonedDateTime} using strict ISO-8601 parsing
     * (e.g. {@code "2023-05-02T01:02:03+02:00[Europe/Paris]"}). For lenient multi-format fallback parsing,
     * use {@link DateUtils}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if the value cannot be parsed as a zoned date-time.
     */
    public static ZonedDateTime toZonedDateTime(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case ZonedDateTime z -> z;
            default -> convert(value, ZonedDateTime.class, v -> ZonedDateTime.parse(v.toString()));
        };
    }

    /**
     * Converts the given value to an enum of the given type, matching names case-insensitively
     * via {@link Enums#getForNameIgnoreCase(String, Class)}.
     *
     * @param value the value to convert.
     * @param enumType the enum class type.
     * @param <T> the enum type.
     * @return the converted value, or {@code null} if the value is {@code null}.
     * @throws TypeConversionException if no enum constant matches the value.
     */
    public static <T extends Enum<T>> T toEnum(final @Nullable Object value, final @NotNull Class<T> enumType) {
        if (value == null) {
            return null;
        }
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        return convert(value, enumType, v -> Enums.getForNameIgnoreCase(v.toString(), enumType));
    }

    /**
     * Converts the given value to a list of strings. A {@link String} is split on commas (tokens are
     * not trimmed); any other value is converted via {@link ListUtils#convertToListString(Object)}.
     *
     * @param value the value to convert.
     * @return the converted value, or {@code null} if the value is {@code null}.
     */
    public static List<String> toListOfString(final @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String csv -> Arrays.asList(csv.split(","));
            default -> ListUtils.convertToListString(value);
        };
    }

    private static <T> T convert(final Object value, final Class<T> targetType, final Function<Object, T> parser) {
        try {
            return parser.apply(value);
        } catch (RuntimeException e) {
            throw new TypeConversionException(conversionErrorMessage(value, targetType), e);
        }
    }

    private static String conversionErrorMessage(final Object value, final Class<?> targetType) {
        return "Cannot convert value '" + value + "' to " + targetType.getSimpleName();
    }

    private TypeConverter() {
    }
}
