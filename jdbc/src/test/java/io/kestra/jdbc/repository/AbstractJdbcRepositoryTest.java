package io.kestra.jdbc.repository;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.jooq.Name;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractJdbcRepositoryTest extends AbstractJdbcRepository {
    private static final List<QueryFilter.Field> fieldsWithSpecificConditions = List.of(
        QueryFilter.Field.QUERY,
        QueryFilter.Field.STATE,
        QueryFilter.Field.CHILD_FILTER,
        QueryFilter.Field.LEVEL,
        QueryFilter.Field.DATE,
        QueryFilter.Field.START_DATE,
        QueryFilter.Field.END_DATE,
        QueryFilter.Field.UPDATED,
        QueryFilter.Field.CREATED,
        QueryFilter.Field.EXPIRATION_DATE,
        QueryFilter.Field.SCOPE,
        QueryFilter.Field.LABELS,
        QueryFilter.Field.TRIGGER_STATE,
        QueryFilter.Field.METADATA,
        QueryFilter.Field.GROUP,
        QueryFilter.Field.NAME,
        QueryFilter.Field.TAGS,
        QueryFilter.Field.ATTEMPT_NUMBER,
        QueryFilter.Field.SUPER_ADMIN,
        QueryFilter.Field.LOCKED,
        QueryFilter.Field.LAST_TRIGGERED_DATE,
        QueryFilter.Field.NEXT_EXECUTION_DATE
    );

    @Test
    void defaultConditions() {
        Arrays.stream(QueryFilter.Field.values()).filter(Predicate.not(fieldsWithSpecificConditions::contains)).forEach(field ->
        {
            String assertValue = "anyValue";
            Name columnName = DSL.quotedName(field.name().toLowerCase());
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.EQUALS)).isEqualTo(
                DSL.field(columnName).eq(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.NOT_EQUALS)).isEqualTo(
                DSL.field(columnName).ne(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.GREATER_THAN)).isEqualTo(
                DSL.field(columnName).greaterThan(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.LESS_THAN)).isEqualTo(
                DSL.field(columnName).lessThan(assertValue)
            );
            assertThat(this.getConditionOnField(field, List.of(assertValue), QueryFilter.Op.IN)).isEqualTo(
                DSL.field(columnName).in(List.of(assertValue))
            );
            assertThat(this.getConditionOnField(field, List.of(assertValue), QueryFilter.Op.NOT_IN)).isEqualTo(
                DSL.field(columnName).notIn(List.of(assertValue))
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.STARTS_WITH)).isEqualTo(
                DSL.field(columnName).startsWith(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.ENDS_WITH)).isEqualTo(
                DSL.field(columnName).endsWith(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.CONTAINS)).isEqualTo(
                DSL.field(columnName).contains(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.REGEX)).isEqualTo(
                DSL.field(columnName).likeRegex(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.PREFIX)).isEqualTo(
                DSL.field(columnName).eq(assertValue)
                    .or(DSL.field(columnName).startsWith(assertValue + "."))
            );
        });
    }

    @Test
    void shouldUseIsNullWhenEqualsValueIsNull() {
        // Given
        Name columnName = DSL.quotedName(QueryFilter.Field.NAMESPACE.name().toLowerCase());

        // When / Then — EQUALS null must generate IS NULL, not col = NULL
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.EQUALS))
            .isEqualTo(DSL.field(columnName).isNull());

        // When / Then — NOT_EQUALS null must generate IS NOT NULL
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.NOT_EQUALS))
            .isEqualTo(DSL.field(columnName).isNotNull());
    }

    @Test
    void shouldThrowWhenNullValueIsUsedWithGreaterThanOrLessThan() {
        // Given / When / Then
        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.GREATER_THAN))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("GREATER_THAN operation requires a non-null value");

        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.LESS_THAN))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("LESS_THAN operation requires a non-null value");
    }

    @Test
    void shouldThrowWhenNullValueIsUsedWithInOrNotIn() {
        // Given / When / Then
        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.IN))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("IN operation requires a non-null value");

        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.NOT_IN))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("NOT_IN operation requires a non-null value");
    }

    @Test
    void shouldThrowWhenListValueIsUsedWithStartsWith() {
        List<String> invalidValue = List.of("val1", "val2");

        assertThatThrownBy(
            () -> this.getConditionOnField(
                QueryFilter.Field.NAMESPACE,
                invalidValue,
                QueryFilter.Op.STARTS_WITH
            )
        )
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("STARTS_WITH operation requires a string value, got a List");
    }

    @Test
    void shouldEscapeWildcardCharactersInLikeOperations() {
        // Given — a value containing SQL LIKE metacharacters
        String wildcardValue = "%";
        String underscoreValue = "_";
        Name columnName = DSL.quotedName(QueryFilter.Field.NAMESPACE.name().toLowerCase());

        // When / Then — CONTAINS: metacharacter must be escaped, not treated as a wildcard
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, wildcardValue, QueryFilter.Op.CONTAINS))
            .isEqualTo(DSL.field(columnName).contains(wildcardValue));

        // When / Then — STARTS_WITH: % in value must not produce an open-ended match
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, wildcardValue, QueryFilter.Op.STARTS_WITH))
            .isEqualTo(DSL.field(columnName).startsWith(wildcardValue));

        // When / Then — ENDS_WITH: _ in value must not act as a single-char wildcard
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, underscoreValue, QueryFilter.Op.ENDS_WITH))
            .isEqualTo(DSL.field(columnName).endsWith(underscoreValue));
    }

    @Test
    void shouldFilterEachDateFieldOnItsOwnColumn() {
        // Given — one instant, applied to every date field
        OffsetDateTime instant = ZonedDateTime.parse("2026-07-01T00:00:00Z").toOffsetDateTime();
        String value = instant.toString();

        // When / Then — each field constrains the column named after it, never a shared one
        assertThat(this.getConditionOnField(QueryFilter.Field.START_DATE, value, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO))
            .isEqualTo(field("start_date").greaterOrEqual(instant));

        assertThat(this.getConditionOnField(QueryFilter.Field.END_DATE, value, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO))
            .isEqualTo(field("end_date").lessOrEqual(instant));

        assertThat(this.getConditionOnField(QueryFilter.Field.DATE, value, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO))
            .isEqualTo(field("date").greaterOrEqual(instant));

        assertThat(this.getConditionOnField(QueryFilter.Field.UPDATED, value, QueryFilter.Op.LESS_THAN))
            .isEqualTo(field("updated").lessThan(instant));

        assertThat(this.getConditionOnField(QueryFilter.Field.EXPIRATION_DATE, value, QueryFilter.Op.GREATER_THAN))
            .isEqualTo(field("expiration_date").greaterThan(instant));

        assertThat(this.getConditionOnField(QueryFilter.Field.CREATED, value, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO))
            .isEqualTo(field("created").greaterOrEqual(instant));
    }

    @Test
    void shouldRejectNullDateValueAsInvalidInput() {
        // Given / When / Then — a missing date value is bad input (422), not a NullPointerException (500),
        // matching how defaultHandlers rejects a null value on a comparison operation.
        assertThatThrownBy(
            () -> this.getConditionOnField(QueryFilter.Field.START_DATE, null, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
        )
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("requires a non-null value");
    }

    @Test
    void tagsConditionShouldDelegateToDefaultHandlers() {
        String assertValue = "my-tag";
        Name columnName = DSL.quotedName(QueryFilter.Field.TAGS.name().toLowerCase());

        assertThat(
            this.getConditionOnField(
                QueryFilter.Field.TAGS,
                List.of(assertValue),
                QueryFilter.Op.IN
            )
        ).isEqualTo(
            DSL.field(columnName).in(List.of(assertValue))
        );
    }
}