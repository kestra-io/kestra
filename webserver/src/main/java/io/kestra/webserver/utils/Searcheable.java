package io.kestra.webserver.utils;

import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for searching over a collection of elements.
 *
 * @param <T> type of the collection elements.
 */
public final class Searcheable<T> {

    private final List<T> items;

    public Searcheable(List<T> items) {
        this.items = items;
    }

    public static <T> Searcheable<T> of(List<T> items) {
        return new Searcheable<>(items);
    }

    public ArrayListTotal<T> search(final Searched<T> searched) {

        List<T> results = items;

        // Quick and naive query search
        if (searched.query() != null && searched.searchableExtractors() != null) {
            final String query = searched.query().toLowerCase();
            results = items.stream()
                .filter(item -> {
                    String search = searched.searchableExtractors()
                        .values()
                        .stream()
                        .map(extractor -> extractor.apply(item))
                        .filter(Objects::nonNull)
                        .map(Objects::toString)
                        .collect(Collectors.joining())
                        .toLowerCase();
                    return search.contains(query);
                }).collect(Collectors.toCollection(ArrayList::new));
        } else {
            results = new ArrayList<>(items);
        }

        Pageable pageable = PageableUtils.from(searched.page(), searched.size(), searched.sort(), null);

        if (pageable.isSorted()) {
            List<Sort.Order> orderBy = pageable.getSort().getOrderBy();
            Comparator<T> comparing = null;
            for (Sort.Order order : orderBy) {
                String property = order.getProperty();
                Function<? super T, Comparable<Object>> keyExtractor = searched.sortableExtractors.get(property);
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
            results.sort(comparing);
        }
        return ArrayListTotal.of(pageable, results);
    }

    /**
     * Searched.
     *
     * @param page  the current page.
     * @param size  the number of element per page.
     * @param sort  the sort
     * @param query the search query.
     * @param searchableExtractors the extractor function for each searchable property.
     * @param sortableExtractors  the extractor function for each sortable property.
     * @param <T>   type of searched element.
     */
    public record Searched<T>(
        int page,
        int size,
        List<String> sort,
        String query,
        Map<String, Function<? super T, Object>> searchableExtractors,
        Map<String, Function<? super T, Comparable<Object>>> sortableExtractors
    ) {

        /**
         * Creates a new {@link Searched} instance.
         *
         * @param <T> type of element.
         * @return a new {@link Builder}.
         */
        public static <T> Builder<T> builder() {
            return new Builder<>();
        }

        public static class Builder<T> {
            private int page = 1;
            private int size = 100;
            private List<String> sort = List.of();
            private String query;
            private final Map<String, Function<? super T, Object>> searchableExtractors = new HashMap<>();
            private final Map<String, Function<? super T, Comparable<Object>>> sortableExtractors = new HashMap<>();

            public Builder<T> page(int page) {
                this.page = page;
                return this;
            }

            public Builder<T> size(int size) {
                this.size = size;
                return this;
            }

            public Builder<T> sort(List<String> sort) {
                this.sort = sort;
                return this;
            }

            public Builder<T> query(String query) {
                this.query = query;
                return this;
            }

            public Builder<T> searchableExtractor(
                final String key,
                final Function<? super T, Object> keyExtractor
            ) {
                this.searchableExtractors.put(key, keyExtractor);
                return this;
            }

            @SuppressWarnings("unchecked")
            public <U extends Comparable<? super U>> Builder<T> sortableExtractor(
                String key,
                Function<? super T, ? extends U> keyExtractor
            ) {
                this.sortableExtractors.put(key, (Function<? super T, Comparable<Object>>) keyExtractor);
                return this;
            }

            public Searched<T> build() {
                return new Searched<>(
                    page,
                    size,
                    sort,
                    query,
                    searchableExtractors,
                    sortableExtractors
                );
            }
        }
    }
}
