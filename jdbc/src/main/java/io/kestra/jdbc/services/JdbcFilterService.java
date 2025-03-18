package io.kestra.jdbc.services;

import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.filters.*;
import io.kestra.core.services.AbstractFilterService;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.Record;

import java.util.Map;

import static org.jooq.impl.DSL.*;

@Singleton
@Requires(bean = AbstractJdbcDashboardRepository.class)
public class JdbcFilterService extends AbstractFilterService<SelectConditionStep<Record>> {
    public AggregateFunction<?> buildAggregation(Field<?> field, AggregationType agg) {

        return switch (agg) {
            case AVG -> avg(field.cast(Double.class));
            case MAX -> max(field.cast(Double.class));
            case MIN -> min(field.cast(Double.class));
            case SUM -> sum(field.cast(Double.class));
            case COUNT -> field != null ? count(field) : count();
        };
    }

    private <F extends Enum<F>> org.jooq.Condition buildWhere(Map<F, String> fieldsMapping, AbstractFilter<F> filter) {
        return switch (filter.getType()) {
            case CONTAINS -> containsCondition(fieldsMapping.get(filter.getField()), (Contains<F>) filter);
            case ENDS_WITH -> endsWithCondition(fieldsMapping.get(filter.getField()), (EndsWith<F>) filter);
            case EQUAL_TO -> equalToCondition(fieldsMapping.get(filter.getField()), (EqualTo<F>) filter);
            case GREATER_THAN -> greaterThanCondition(fieldsMapping.get(filter.getField()), (GreaterThan<F>) filter);
            case GREATER_THAN_OR_EQUAL_TO -> greaterThanOrEqualToCondition(fieldsMapping.get(filter.getField()), (GreaterThanOrEqualTo<F>) filter);
            case IN -> inCondition(fieldsMapping.get(filter.getField()), (In<F>) filter);
            case IS_FALSE -> isFalseCondition(fieldsMapping.get(filter.getField()), (IsFalse<F>) filter);
            case IS_NOT_NULL -> isNotNullCondition(fieldsMapping.get(filter.getField()), (IsNotNull<F>) filter);
            case IS_NULL -> isNullCondition(fieldsMapping.get(filter.getField()), (IsNull<F>) filter);
            case IS_TRUE -> isTrueCondition(fieldsMapping.get(filter.getField()), (IsTrue<F>) filter);
            case LESS_THAN -> lessThanCondition(fieldsMapping.get(filter.getField()), (LessThan<F>) filter);
            case LESS_THAN_OR_EQUAL_TO -> lessThanOrEqualToCondition(fieldsMapping.get(filter.getField()), (LessThanOrEqualTo<F>) filter);
            case NOT_EQUAL_TO -> notEqualToCondition(fieldsMapping.get(filter.getField()), (NotEqualTo<F>) filter);
            case NOT_IN -> notInCondition(fieldsMapping.get(filter.getField()), (NotIn<F>) filter);
            case OR -> orCondition(fieldsMapping, (Or<F>) filter);
            case REGEX -> regexCondition(fieldsMapping.get(filter.getField()), (Regex<F>) filter);
            case STARTS_WITH -> startsWithCondition(fieldsMapping.get(filter.getField()), (StartsWith<F>) filter);
        };
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> contains(SelectConditionStep<Record> query, String field, Contains<F> filter) {
        return query.and(field(field).contains(filter.getValue().toString()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> endsWith(SelectConditionStep<Record> query, String field, EndsWith<F> filter) {
        return query.and(field(field).endsWith(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> equalTo(SelectConditionStep<Record> query, String field, EqualTo<F> filter) {
        return query.and(field(field).eq(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> greaterThan(SelectConditionStep<Record> query, String field, GreaterThan<F> filter) {
        return query.and(field(field).gt(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> greaterThanOrEqualTo(SelectConditionStep<Record> query, String field, GreaterThanOrEqualTo<F> filter) {
        return query.and(field(field).ge(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> in(SelectConditionStep<Record> query, String field, In<F> filter) {
        return query.and(field(field).in(filter.getValues()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> isFalse(SelectConditionStep<Record> query, String field, IsFalse<F> filter) {
        return query.and(field(field).isFalse());
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> isNotNull(SelectConditionStep<Record> query, String field, IsNotNull<F> filter) {
        return query.and(field(field).isNotNull());
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> isNull(SelectConditionStep<Record> query, String field, IsNull<F> filter) {
        return query.and(field(field).isNull());
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> isTrue(SelectConditionStep<Record> query, String field, IsTrue<F> filter) {
        return query.and(field(field).isTrue());
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> lessThan(SelectConditionStep<Record> query, String field, LessThan<F> filter) {
        return query.and(field(field).lt(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> lessThanOrEqualTo(SelectConditionStep<Record> query, String field, LessThanOrEqualTo<F> filter) {
        return query.and(field(field).le(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> notEqualTo(SelectConditionStep<Record> query, String field, NotEqualTo<F> filter) {
        return query.and(field(field).ne(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> notIn(SelectConditionStep<Record> query, String field, NotIn<F> filter) {
        return query.and(field(field).notIn(filter.getValues()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> or(SelectConditionStep<Record> query, Map<F, String> fieldsMapping, Or<F> filter) {
        SelectConditionStep<Record> orQuery = query;
        for (AbstractFilter<F> subFilter : filter.getValues()) {
            orQuery = orQuery.or(this.buildWhere(fieldsMapping, subFilter));
        }
        return orQuery;
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> regex(SelectConditionStep<Record> query, String field, Regex<F> filter) {
        return query.and(field(field).likeRegex(filter.getValue()));
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> startsWith(SelectConditionStep<Record> query, String field, StartsWith<F> filter) {
        return query.and(field(field).startsWith(filter.getValue()));
    }

    private <F extends Enum<F>> org.jooq.Condition containsCondition(String field, Contains<F> filter) {
        return field(field).contains(filter.getValue().toString());
    }

    private <F extends Enum<F>> org.jooq.Condition endsWithCondition(String field, EndsWith<F> filter) {
        return field(field).endsWith(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition equalToCondition(String field, EqualTo<F> filter) {
        return field(field).eq(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition greaterThanCondition(String field, GreaterThan<F> filter) {
        return field(field).gt(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition greaterThanOrEqualToCondition(String field, GreaterThanOrEqualTo<F> filter) {
        return field(field).ge(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition inCondition(String field, In<F> filter) {
        return field(field).in(filter.getValues());
    }

    private <F extends Enum<F>> org.jooq.Condition isFalseCondition(String field, IsFalse<F> filter) {
        return field(field).isFalse();
    }

    private <F extends Enum<F>> org.jooq.Condition isNotNullCondition(String field, IsNotNull<F> filter) {
        return field(field).isNotNull();
    }

    private <F extends Enum<F>> org.jooq.Condition isNullCondition(String field, IsNull<F> filter) {
        return field(field).isNull();
    }

    private <F extends Enum<F>> org.jooq.Condition isTrueCondition(String field, IsTrue<F> filter) {
        return field(field).isTrue();
    }

    private <F extends Enum<F>> org.jooq.Condition lessThanCondition(String field, LessThan<F> filter) {
        return field(field).lt(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition lessThanOrEqualToCondition(String field, LessThanOrEqualTo<F> filter) {
        return field(field).le(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition notEqualToCondition(String field, NotEqualTo<F> filter) {
        return field(field).ne(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition notInCondition(String field, NotIn<F> filter) {
        return field(field).notIn(filter.getValues());
    }

    private <F extends Enum<F>> org.jooq.Condition orCondition(Map<F, String> fieldsMapping, Or<F> filter) {
        Condition orCondition = falseCondition();
        for (AbstractFilter<F> subFilter : filter.getValues()) {
            orCondition = orCondition.or(buildWhere(fieldsMapping, subFilter));
        }
        return orCondition;
    }

    private <F extends Enum<F>> org.jooq.Condition regexCondition(String field, Regex<F> filter) {
        return field(field).likeRegex(filter.getValue());
    }

    private <F extends Enum<F>> org.jooq.Condition startsWithCondition(String field, StartsWith<F> filter) {
        return field(field).startsWith(filter.getValue());
    }

}
