package io.kestra.jdbc.services;

import io.kestra.core.models.dashboards.filters.*;
import io.kestra.core.services.AbstractFilterService;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;
import org.jooq.SelectFromStep;

import java.util.Map;

@Singleton
@Requires(bean = AbstractJdbcDashboardRepository.class)
public class JdbcFilterService extends AbstractFilterService<SelectFromStep<?>> {
    @Override
    protected <F extends Enum<F>> SelectFromStep<?> contains(SelectFromStep<?> query, String field, Contains<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> endsWith(SelectFromStep<?> query, String field, EndsWith<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> equalTo(SelectFromStep<?> query, String field, EqualTo<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> greaterThan(SelectFromStep<?> query, String field, GreaterThan<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> greaterThanOrEqualTo(SelectFromStep<?> query, String field, GreaterThanOrEqualTo<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> in(SelectFromStep<?> query, String field, In<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> isFalse(SelectFromStep<?> query, String field, IsFalse<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> isNotNull(SelectFromStep<?> query, String field, IsNotNull<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> isNull(SelectFromStep<?> query, String field, IsNull<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> isTrue(SelectFromStep<?> query, String field, IsTrue<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> lessThan(SelectFromStep<?> query, String field, LessThan<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> lessThanOrEqualTo(SelectFromStep<?> query, String field, LessThanOrEqualTo<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> notEqualTo(SelectFromStep<?> query, String field, NotEqualTo<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> notIn(SelectFromStep<?> query, String field, NotIn<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> or(SelectFromStep<?> query, Map<F, String> fieldsMapping, Or<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> regex(SelectFromStep<?> query, String field, Regex<F> filter) {
        throw new NotImplementedException();
    }

    @Override
    protected <F extends Enum<F>> SelectFromStep<?> startsWith(SelectFromStep<?> query, String field, StartsWith<F> filter) {
        throw new NotImplementedException();
    }
}
