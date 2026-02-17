package io.kestra.jdbc.repository;

import io.kestra.core.models.QueryFilter;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJdbcRepositoryTest extends AbstractJdbcRepository {
    // TODO add dedicated tests for those fields with specific conditions
    // Those fields have specific conditions (not simple JOOQ operations) and are tested in dedicated tests, so we exclude them from the default conditions test
    private static final List<QueryFilter.Field> fieldsWithSpecificConditions = List.of(
        QueryFilter.Field.QUERY,
        QueryFilter.Field.STATE,
        QueryFilter.Field.CHILD_FILTER,
        QueryFilter.Field.MIN_LEVEL,
        QueryFilter.Field.START_DATE,
        QueryFilter.Field.END_DATE,
        QueryFilter.Field.UPDATED,
        QueryFilter.Field.CREATED,
        QueryFilter.Field.EXPIRATION_DATE,
        QueryFilter.Field.EXPIRATION_DATE,
        QueryFilter.Field.SCOPE,
        QueryFilter.Field.LABELS,
        QueryFilter.Field.TRIGGER_STATE,
        QueryFilter.Field.METADATA
    );

    @Test
    public void defaultConditions() {
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
                DSL.field(columnName).like(assertValue + "%")
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.ENDS_WITH, null)).isEqualTo(
                DSL.field(columnName).like("%" + assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.CONTAINS, null)).isEqualTo(
                DSL.field(columnName).like("%" + assertValue + "%")
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.REGEX, null)).isEqualTo(
                DSL.field(columnName).likeRegex(assertValue)
            );
            assertThat(this.getConditionOnField(field, assertValue, QueryFilter.Op.PREFIX, null)).isEqualTo(
                DSL.field(columnName).like(assertValue + ".%")
                    .or(DSL.field(columnName).eq(assertValue))
            );
        });
    }
}
