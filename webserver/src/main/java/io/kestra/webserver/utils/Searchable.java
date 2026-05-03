package io.kestra.webserver.utils;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

/**
 * Reusable, pre-configured in-memory filter for a collection of elements.
 * Build once with predicates via {@link Builder}, then call {@link #filter} with items and runtime parameters.
 *
 * @param <T> type of the collection elements.
 */
public final class Searchable<T> {

    public record QueryFilterPredicateKey(QueryFilter.Field field, QueryFilter.Op operator) {}

    private final Map<String, Function<? super T, Object>> searchableExtractors;
    private final Map<String, Function<? super T, Comparable<Object>>> sortableExtractors;
    private final Map<QueryFilterPredicateKey, BiPredicate<T, Object>> queryFilterPredicateMap;

    private Searchable(
        Map<String, Function<? super T, Object>> searchableExtractors,
        Map<String, Function<? super T, Comparable<Object>>> sortableExtractors,
        Map<QueryFilterPredicateKey, BiPredicate<T, Object>> queryFilterPredicateMap) {
        this.searchableExtractors = searchableExtractors;
        this.sortableExtractors = sortableExtractors;
        this.queryFilterPredicateMap = queryFilterPredicateMap;
    }

    public Set<QueryFilterPredicateKey>     registeredPredicateKeys() {
        return queryFilterPredicateMap.keySet();
    }

    public ArrayListTotal<T> filter(
        List<T> items,
        int page,
        int size,
        @Nullable List<String> sort,
        @Nullable List<QueryFilter> queryFilters
    ) {
        return filter(items, page, size, sort, queryFilters, null);
    }

    public ArrayListTotal<T> filter(
        List<T> items,
        int page,
        int size,
        @Nullable List<String> sort,
        @Nullable List<QueryFilter> queryFilters,
        @Nullable String query) {

        Stream<T> results = items.stream();

        if (query != null && !searchableExtractors.isEmpty()) {
            final String q = query.toLowerCase();
            results = results.filter(item -> {
                String search = searchableExtractors.values().stream()
                    .map(extractor -> extractor.apply(item))
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .collect(Collectors.joining())
                    .toLowerCase();
                return search.contains(q);
            });
        }

        if (queryFilters != null && !queryFilters.isEmpty()) {
            results = results.filter(item ->
                queryFilters.stream().allMatch(queryFilter -> {
                    BiPredicate<T, Object> predicate = queryFilterPredicateMap.get(
                        new QueryFilterPredicateKey(queryFilter.field(), queryFilter.operation())
                    );
                    if (predicate == null) {
                        throw new InvalidQueryFiltersException(
                            "Unsupported operation for " + queryFilter.field() + ": " + queryFilter.operation()
                        );
                    }
                    return predicate.test(item, queryFilter.value());
                })
            );
        }

        Pageable pageable = PageableUtils.from(page, size, sort, null);

        if (pageable.isSorted()) {
            List<Sort.Order> orderBy = pageable.getSort().getOrderBy();
            Comparator<T> comparing = null;
            for (Sort.Order order : orderBy) {
                String property = order.getProperty();
                Function<? super T, Comparable<Object>> keyExtractor = sortableExtractors.get(property);
                if (keyExtractor != null) {
                    if (comparing == null) {
                        comparing = Comparator.comparing(
                            keyExtractor,
                            order.isAscending() ? Comparator.naturalOrder() : Comparator.reverseOrder()
                        );
                    } else {
                        comparing = comparing.thenComparing(
                            keyExtractor,
                            order.isAscending() ? Comparator.naturalOrder() : Comparator.reverseOrder()
                        );
                    }
                }
            }
            if (comparing != null) {
                results = results.sorted(comparing);
            }
        }

        return ArrayListTotal.of(pageable, results.toList());
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private final Map<String, Function<? super T, Object>> searchableExtractors = new HashMap<>();
        private final Map<String, Function<? super T, Comparable<Object>>> sortableExtractors = new HashMap<>();
        private final Map<QueryFilterPredicateKey, BiPredicate<T, Object>> queryFilterPredicateMap = new HashMap<>();

        public Builder<T> searchableExtractor(String key, Function<? super T, Object> extractor) {
            this.searchableExtractors.put(key, extractor);
            return this;
        }

        public <F extends QueryFilter.Field, O extends QueryFilter.Op> Builder<T> searchableQueryFilterExtractor(
            F field, O operator, BiPredicate<T, Object> predicate) {
            this.queryFilterPredicateMap.put(new QueryFilterPredicateKey(field, operator), predicate);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <U extends Comparable<? super U>> Builder<T> sortableExtractor(
            String key,
            Function<? super T, ? extends U> extractor) {
            this.sortableExtractors.put(key, (Function<? super T, Comparable<Object>>) extractor);
            return this;
        }

        public Searchable<T> build() {
            return new Searchable<>(
                Map.copyOf(searchableExtractors),
                Map.copyOf(sortableExtractors),
                Map.copyOf(queryFilterPredicateMap)
            );
        }
    }
}
