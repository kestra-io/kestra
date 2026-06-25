package io.kestra.jdbc.repository;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractJdbcRepositoryTest extends AbstractJdbcRepository {
    private static final List<QueryFilter.Field> fieldsWithSpecificConditions = List.of(
        QueryFilter.Field.QUERY,
        QueryFilter.Field.STATE,
        QueryFilter.Field.CHILD_FILTER,
        QueryFilter.Field.LEVEL,
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
        QueryFilter.Field.NEXT_EXECUTION_DATE,
        QueryFilter.Field.TIME_RANGE
    );

    @Test
    void defaultConditions() {
        Arrays.stream(QueryFilter.Field.values()).filter(Predicate.not(fieldsWithSpecificConditions::contains)).forEach(field -> {
            String assertValue = "anyValue";
            Name columnName = DSL.quotedName(field.name().toLowerCase());
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.EQUALS, null)).isEqualTo(
                DSL.field(columnName).eq(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.NOT_EQUALS, null)).isEqualTo(
                DSL.field(columnName).ne(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.GREATER_THAN, null)).isEqualTo(
                DSL.field(columnName).greaterThan(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.LESS_THAN, null)).isEqualTo(
                DSL.field(columnName).lessThan(assertValue)
            );
            assertThat(this.getConditionOnField(field, List.of(assertValue), QueryFilter.Op.IN, null)).isEqualTo(
                DSL.field(columnName).in(List.of(assertValue))
            );
            assertThat(this.getConditionOnField(field, List.of(assertValue), QueryFilter.Op.NOT_IN, null)).isEqualTo(
                DSL.field(columnName).notIn(List.of(assertValue))
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.STARTS_WITH, null)).isEqualTo(
                DSL.field(columnName).startsWith(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.ENDS_WITH, null)).isEqualTo(
                DSL.field(columnName).endsWith(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.CONTAINS, null)).isEqualTo(
                DSL.field(columnName).contains(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.REGEX, null)).isEqualTo(
                DSL.field(columnName).likeRegex(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.PREFIX, null)).isEqualTo(
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
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.EQUALS, null))
            .isEqualTo(DSL.field(columnName).isNull());

        // When / Then — NOT_EQUALS null must generate IS NOT NULL
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.NOT_EQUALS, null))
            .isEqualTo(DSL.field(columnName).isNotNull());
    }

    @Test
    void shouldThrowWhenNullValueIsUsedWithGreaterThanOrLessThan() {
        // Given / When / Then
        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.GREATER_THAN, null))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("GREATER_THAN operation requires a non-null value");

        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.LESS_THAN, null))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("LESS_THAN operation requires a non-null value");
    }

    @Test
    void shouldThrowWhenNullValueIsUsedWithInOrNotIn() {
        // Given / When / Then
        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.IN, null))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("IN operation requires a non-null value");

        assertThatThrownBy(() -> this.getConditionOnField(QueryFilter.Field.NAMESPACE, null, QueryFilter.Op.NOT_IN, null))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("NOT_IN operation requires a non-null value");
    }

    @Test
    void shouldThrowWhenListValueIsUsedWithStartsWith() {
        List<String> invalidValue = List.of("val1", "val2");

        assertThatThrownBy(() -> this.getConditionOnField(
            QueryFilter.Field.NAMESPACE,
            invalidValue,
            QueryFilter.Op.STARTS_WITH,
            null
        ))
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
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, wildcardValue, QueryFilter.Op.CONTAINS, null))
            .isEqualTo(DSL.field(columnName).contains(wildcardValue));

        // When / Then — STARTS_WITH: % in value must not produce an open-ended match
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, wildcardValue, QueryFilter.Op.STARTS_WITH, null))
            .isEqualTo(DSL.field(columnName).startsWith(wildcardValue));

        // When / Then — ENDS_WITH: _ in value must not act as a single-char wildcard
        assertThat(this.getConditionOnField(QueryFilter.Field.NAMESPACE, underscoreValue, QueryFilter.Op.ENDS_WITH, null))
            .isEqualTo(DSL.field(columnName).endsWith(underscoreValue));
    }

    @Test
    void tagsConditionShouldDelegateToDefaultHandlers() {
        String assertValue = "my-tag";
        Name columnName = DSL.quotedName(QueryFilter.Field.TAGS.name().toLowerCase());
    
        assertThat(
            this.getConditionOnField(
                QueryFilter.Field.TAGS,
                List.of(assertValue),
                QueryFilter.Op.IN,
                null
            )
        ).isEqualTo(
            DSL.field(columnName).in(List.of(assertValue))
        );
    }
}