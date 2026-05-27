package io.kestra.webserver.services;

import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.utils.RegexUtils;
import io.kestra.webserver.utils.Searchable;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class SearchableFactory {

    @Singleton
    @Named("NAMESPACE")
    public Searchable<Namespace> namespaceSearchable() {
        return Searchable.<Namespace>builder()
            .searchableExtractor("id", Namespace::getId)
            .sortableExtractor("id", Namespace::getId)
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.EQUALS,
                (ns, v) -> ns.getId().toLowerCase().contains(v.toString().toLowerCase()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.EQUALS,
                (ns, v) -> ns.getId().equals(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.NOT_EQUALS,
                (ns, v) -> !ns.getId().equals(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.CONTAINS,
                (ns, v) -> ns.getId().contains(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.STARTS_WITH,
                (ns, v) -> ns.getId().startsWith(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.ENDS_WITH,
                (ns, v) -> ns.getId().endsWith(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.REGEX,
                (ns, v) -> RegexUtils.matches(v.toString(), ns.getId()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.IN,
                (ns, v) -> v instanceof List<?> list && list.stream().map(Object::toString).anyMatch(ns.getId()::equals))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.NOT_IN,
                (ns, v) -> !(v instanceof List<?> list && list.stream().map(Object::toString).anyMatch(ns.getId()::equals)))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, QueryFilter.Op.PREFIX,
                (ns, v) -> ns.getId().equals(v.toString()) || ns.getId().startsWith(v + "."))
            .build();
    }
}
