package io.kestra.core.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.dashboards.filters.AbstractFilter.FilterType;
import io.kestra.core.utils.Patterns;
import io.kestra.core.utils.RegexUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A match predicate on one field, in the dashboard filter grammar. Text operators compare the string form of both
 * sides, case-sensitively; the ordering operators compare numbers and ISO-8601 dates by value.
 */
@Schema(title = "A condition on one field of a resource.")
public record FieldCondition(
    @NotBlank
    @Schema(title = "The field this condition reads.") String field,

    @NotNull
    @Schema(title = "How the field is compared to the value.") FilterType operator,

    @Nullable
    @Schema(
        title = "The value the field is compared to.",
        description = "A list for `IN` and `NOT_IN`, ignored by `IS_NULL` and `IS_NOT_NULL`.",
        anyOf = {
            String.class, Double.class, Boolean.class, List.class }
    ) Object value){

    private static final Set<FilterType> UNSUPPORTED_OPERATORS = EnumSet.of(FilterType.OR, FilterType.IS_TRUE, FilterType.IS_FALSE);

    @AssertTrue(message = "operator is not supported: OR, IS_TRUE and IS_FALSE do not compare a field to a value")
    @JsonIgnore
    public boolean isOperatorSupported() {
        return operator == null || !UNSUPPORTED_OPERATORS.contains(operator);
    }

    @AssertTrue(message = "value is required unless operator is IS_NULL or IS_NOT_NULL")
    @JsonIgnore
    public boolean isValueGivenWhenCompared() {
        return operator == null || FilterType.IS_NULL == operator || FilterType.IS_NOT_NULL == operator
            || (value != null && !"".equals(value));
    }

    @AssertTrue(message = "value must be a non-empty list for IN and NOT_IN, and a single value for the other operators")
    @JsonIgnore
    public boolean isValueShapedForOperator() {
        if (FilterType.IN == operator || FilterType.NOT_IN == operator) {
            return value instanceof Collection<?> values && !values.isEmpty();
        }
        return !(value instanceof Collection<?>);
    }

    @AssertTrue(message = "value is not a valid regular expression, or it is too long or prone to catastrophic backtracking")
    @JsonIgnore
    public boolean isRegexSafe() {
        if (FilterType.REGEX != operator || value == null) {
            return true;
        }
        String regex = value.toString();
        return RegexUtils.isSafeUserRegex(regex) && RegexUtils.syntaxError(regex).isEmpty();
    }

    public boolean test(@Nullable Object actual) {
        if (operator == null) {
            return false;
        }
        String actualText = actual == null ? null : actual.toString();
        String expected = value == null ? null : value.toString();
        return switch (operator) {
            case EQUAL_TO -> Objects.equals(actualText, expected);
            case NOT_EQUAL_TO -> !Objects.equals(actualText, expected);
            case STARTS_WITH, PREFIX -> actualText != null && expected != null && actualText.startsWith(expected);
            case ENDS_WITH -> actualText != null && expected != null && actualText.endsWith(expected);
            case CONTAINS -> contains(actualText, expected);
            case NOT_CONTAINS -> !contains(actualText, expected);
            case REGEX -> actualText != null && expected != null && RegexUtils.matches(Patterns.of(expected), actualText);
            case IN -> isListed(actualText);
            case NOT_IN -> !isListed(actualText);
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
            case LESS_THAN -> isOrdered(actual, result -> result < 0);
            case LESS_THAN_OR_EQUAL_TO -> isOrdered(actual, result -> result <= 0);
            case GREATER_THAN -> isOrdered(actual, result -> result > 0);
            case GREATER_THAN_OR_EQUAL_TO -> isOrdered(actual, result -> result >= 0);
            case OR, IS_TRUE, IS_FALSE -> false;
        };
    }

    private static boolean contains(@Nullable String actual, @Nullable String expected) {
        return actual != null && expected != null && actual.contains(expected);
    }

    private boolean isListed(@Nullable String actual) {
        return value instanceof Collection<?> values && values.stream().anyMatch(candidate -> Objects.equals(actual, Objects.toString(candidate, null)));
    }

    private boolean isOrdered(@Nullable Object actual, IntPredicate accept) {
        return compare(actual, value).filter(accept::test).isPresent();
    }

    private static Optional<Integer> compare(@Nullable Object left, @Nullable Object right) {
        Optional<BigDecimal> leftNumber = asNumber(left);
        Optional<BigDecimal> rightNumber = asNumber(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return Optional.of(leftNumber.get().compareTo(rightNumber.get()));
        }

        Optional<Instant> leftInstant = asInstant(left);
        Optional<Instant> rightInstant = asInstant(right);
        if (leftInstant.isPresent() && rightInstant.isPresent()) {
            return Optional.of(leftInstant.get().compareTo(rightInstant.get()));
        }

        // A blank value next to a number or a date has no order: an empty ttl means "no expiry", not "before every date".
        if (leftNumber.isPresent() || rightNumber.isPresent() || leftInstant.isPresent() || rightInstant.isPresent()) {
            return Optional.empty();
        }

        if (left instanceof String leftText && right instanceof String rightText) {
            return Optional.of(Integer.signum(leftText.compareTo(rightText)));
        }

        return Optional.empty();
    }

    private static Optional<BigDecimal> asNumber(@Nullable Object value) {
        if (value instanceof Number number) {
            return asNumber(number.toString());
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // A date-time without an offset is refused rather than read in the server's zone, which the author never saw.
    private static Optional<Instant> asInstant(@Nullable Object value) {
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        try {
            return Optional.of(OffsetDateTime.parse(trimmed).toInstant());
        } catch (DateTimeParseException e) {
            try {
                return Optional.of(LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
    }
}
