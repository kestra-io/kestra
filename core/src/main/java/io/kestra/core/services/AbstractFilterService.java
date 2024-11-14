package io.kestra.core.services;

import io.kestra.core.models.dashboards.filters.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractFilterService<Q> {
    public <F extends Enum<F>> Q addFilters(Q query, Map<F, String> fieldsMapping, List<AbstractFilter<F>> filters) {
        AtomicReference<Q> finalQuery = new AtomicReference<>(query);
        filters.forEach(filter ->
            finalQuery.set(
                switch (filter) {
                    case Contains<F> f -> contains(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case EndsWith<F> f -> endsWith(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case EqualTo<F> f -> equalTo(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case GreaterThan<F> f -> greaterThan(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case GreaterThanOrEqualTo<F> f -> greaterThanOrEqualTo(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case In<F> f -> in(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case IsFalse<F> f -> isFalse(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case IsNotNull<F> f -> isNotNull(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case IsNull<F> f -> isNull(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case IsTrue<F> f -> isTrue(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case LessThan<F> f -> lessThan(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case LessThanOrEqualTo<F> f -> lessThanOrEqualTo(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case NotEqualTo<F> f -> notEqualTo(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case NotIn<F> f -> notIn(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case Or<F> f -> or(finalQuery.get(), fieldsMapping, f);
                    case Regex<F> f -> regex(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    case StartsWith<F> f -> startsWith(finalQuery.get(), fieldsMapping.get(f.getField()), f);
                    default ->
                        throw new UnsupportedOperationException(filter.getClass().getName() + " is not implemented.");
                })
        );

        return finalQuery.get();
    }

    protected abstract <F extends Enum<F>> Q contains(Q query, String field, Contains<F> filter);

    protected abstract <F extends Enum<F>> Q endsWith(Q query, String field, EndsWith<F> filter);

    protected abstract <F extends Enum<F>> Q equalTo(Q query, String field, EqualTo<F> filter);

    protected abstract <F extends Enum<F>> Q greaterThan(Q query, String field, GreaterThan<F> filter);

    protected abstract <F extends Enum<F>> Q greaterThanOrEqualTo(Q query, String field, GreaterThanOrEqualTo<F> filter);

    protected abstract <F extends Enum<F>> Q in(Q query, String field, In<F> filter);

    protected abstract <F extends Enum<F>> Q isFalse(Q query, String field, IsFalse<F> filter);

    protected abstract <F extends Enum<F>> Q isNotNull(Q query, String field, IsNotNull<F> filter);

    protected abstract <F extends Enum<F>> Q isNull(Q query, String field, IsNull<F> filter);

    protected abstract <F extends Enum<F>> Q isTrue(Q query, String field, IsTrue<F> filter);

    protected abstract <F extends Enum<F>> Q lessThan(Q query, String field, LessThan<F> filter);

    protected abstract <F extends Enum<F>> Q lessThanOrEqualTo(Q query, String field, LessThanOrEqualTo<F> filter);

    protected abstract <F extends Enum<F>> Q notEqualTo(Q query, String field, NotEqualTo<F> filter);

    protected abstract <F extends Enum<F>> Q notIn(Q query, String field, NotIn<F> filter);

    protected abstract <F extends Enum<F>> Q or(Q query, Map<F, String> fieldsMapping, Or<F> filter);

    protected abstract <F extends Enum<F>> Q regex(Q query, String field, Regex<F> filter);

    protected abstract <F extends Enum<F>> Q startsWith(Q query, String field, StartsWith<F> filter);
}
