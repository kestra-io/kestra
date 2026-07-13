package io.kestra.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.exceptions.TypeConversionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeConverterTest {

    private enum TestEnum {
        FIRST,
        SECOND
    }

    private static final Function<Object, TestEnum> TO_TEST_ENUM = value -> TypeConverter.toEnum(value, TestEnum.class);

    @ParameterizedTest
    @MethodSource("converters")
    void shouldReturnNullWhenInputIsNull(Function<Object, ?> converter) {
        assertThat(converter.apply(null)).isNull();
    }

    private static Stream<Arguments> converters() {
        return Stream.of(
            arguments("toBoolean", TypeConverter::toBoolean),
            arguments("toInteger", TypeConverter::toInteger),
            arguments("toLong", TypeConverter::toLong),
            arguments("toFloat", TypeConverter::toFloat),
            arguments("toDouble", TypeConverter::toDouble),
            arguments("toDuration", TypeConverter::toDuration),
            arguments("toInstant", TypeConverter::toInstant),
            arguments("toLocalDate", TypeConverter::toLocalDate),
            arguments("toLocalTime", TypeConverter::toLocalTime),
            arguments("toZonedDateTime", TypeConverter::toZonedDateTime),
            arguments("toEnum", TO_TEST_ENUM),
            arguments("toListOfString", TypeConverter::toListOfString)
        );
    }

    @ParameterizedTest
    @MethodSource("passthroughValues")
    void shouldReturnSameInstanceWhenAlreadyTargetType(Function<Object, ?> converter, Object value) {
        assertThat(converter.apply(value)).isSameAs(value);
    }

    private static Stream<Arguments> passthroughValues() {
        return Stream.of(
            arguments("toBoolean", TypeConverter::toBoolean, Boolean.TRUE),
            arguments("toInteger", TypeConverter::toInteger, Integer.valueOf(42)),
            arguments("toLong", TypeConverter::toLong, Long.valueOf(42L)),
            arguments("toFloat", TypeConverter::toFloat, Float.valueOf(1.5f)),
            arguments("toDouble", TypeConverter::toDouble, Double.valueOf(1.5d)),
            arguments("toDuration", TypeConverter::toDuration, Duration.ofHours(1)),
            arguments("toInstant", TypeConverter::toInstant, Instant.parse("2023-05-02T01:02:03Z")),
            arguments("toLocalDate", TypeConverter::toLocalDate, LocalDate.of(2023, 5, 2)),
            arguments("toLocalTime", TypeConverter::toLocalTime, LocalTime.of(1, 2, 3)),
            arguments("toZonedDateTime", TypeConverter::toZonedDateTime, ZonedDateTime.parse("2023-05-02T01:02:03+02:00[Europe/Paris]")),
            arguments("toEnum", TO_TEST_ENUM, TestEnum.FIRST)
        );
    }

    @ParameterizedTest
    @MethodSource("validConversions")
    void shouldConvertWhenValidInput(Function<Object, ?> converter, Object input, Object expected) {
        assertThat(converter.apply(input)).isEqualTo(expected);
    }

    private static Stream<Arguments> validConversions() {
        return Stream.of(
            arguments("toBoolean", TypeConverter::toBoolean, "true", true),
            arguments("toBoolean", TypeConverter::toBoolean, "TrUe", true),
            arguments("toBoolean", TypeConverter::toBoolean, "false", false),
            // only the literal "true" is true — "1", "yes" and non-boolean objects are false, never an error
            arguments("toBoolean", TypeConverter::toBoolean, "1", false),
            arguments("toBoolean", TypeConverter::toBoolean, "yes", false),
            arguments("toBoolean", TypeConverter::toBoolean, 1, false),
            arguments("toInteger", TypeConverter::toInteger, "42", 42),
            arguments("toInteger", TypeConverter::toInteger, 42L, 42),
            arguments("toLong", TypeConverter::toLong, "3000000000", 3_000_000_000L),
            arguments("toLong", TypeConverter::toLong, 42, 42L),
            arguments("toFloat", TypeConverter::toFloat, "1.5", 1.5f),
            arguments("toDouble", TypeConverter::toDouble, "1.5", 1.5d),
            arguments("toDuration", TypeConverter::toDuration, "PT24H", Duration.ofHours(24)),
            arguments("toInstant", TypeConverter::toInstant, "2023-05-02T01:02:03Z", Instant.parse("2023-05-02T01:02:03Z")),
            arguments("toLocalDate", TypeConverter::toLocalDate, "2023-05-02", LocalDate.of(2023, 5, 2)),
            arguments("toLocalTime", TypeConverter::toLocalTime, "01:02:03", LocalTime.of(1, 2, 3)),
            arguments("toZonedDateTime", TypeConverter::toZonedDateTime, "2023-05-02T01:02:03+02:00[Europe/Paris]", ZonedDateTime.parse("2023-05-02T01:02:03+02:00[Europe/Paris]")),
            arguments("toEnum", TO_TEST_ENUM, "first", TestEnum.FIRST),
            arguments("toEnum", TO_TEST_ENUM, "SECOND", TestEnum.SECOND),
            arguments("toListOfString", TypeConverter::toListOfString, "a,b,c", List.of("a", "b", "c")),
            // tokens are not trimmed
            arguments("toListOfString", TypeConverter::toListOfString, "a, b", List.of("a", " b")),
            arguments("toListOfString", TypeConverter::toListOfString, "a", List.of("a")),
            arguments("toListOfString", TypeConverter::toListOfString, List.of(1, 2), List.of("1", "2"))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConversions")
    void shouldThrowTypeConversionExceptionWhenInvalidInput(Function<Object, ?> converter, Object input, Class<? extends Throwable> causeType) {
        assertThatThrownBy(() -> converter.apply(input))
            .isInstanceOf(TypeConversionException.class)
            .hasCauseInstanceOf(causeType);
    }

    private static Stream<Arguments> invalidConversions() {
        return Stream.of(
            arguments("toInteger", TypeConverter::toInteger, "not-a-number", NumberFormatException.class),
            arguments("toInteger", TypeConverter::toInteger, 3_000_000_000L, NumberFormatException.class),
            arguments("toLong", TypeConverter::toLong, "1.5", NumberFormatException.class),
            arguments("toFloat", TypeConverter::toFloat, "abc", NumberFormatException.class),
            arguments("toDuration", TypeConverter::toDuration, "24h", DateTimeParseException.class),
            // documents the strictness relied on by callers: only ISO instants, no zoned formats
            arguments("toInstant", TypeConverter::toInstant, "2023-05-02T01:02:03+02:00[Europe/Paris]", DateTimeParseException.class),
            arguments("toLocalDate", TypeConverter::toLocalDate, "02/05/2023", DateTimeParseException.class),
            arguments("toZonedDateTime", TypeConverter::toZonedDateTime, "2023-05-02", DateTimeParseException.class),
            arguments("toEnum", TO_TEST_ENUM, "THIRD", IllegalArgumentException.class)
        );
    }

    private static Arguments arguments(String name, Function<Object, ?> converter, Object... rest) {
        return Arguments.of(Stream.concat(Stream.of(Named.of(name, converter)), Stream.of(rest)).toArray());
    }
}
