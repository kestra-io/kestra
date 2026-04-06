package io.kestra.webserver.utils;

import java.util.List;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.ArrayListTotal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearcheableTest {
    private Searcheable<TestEntity> searchable;

    @BeforeEach
    void setUp() {
        List<TestEntity> entities = List.of(
            new TestEntity("Alice", 30),
            new TestEntity("Bob", 25),
            new TestEntity("Charlie", 35),
            new TestEntity("Alice", 40)
        );
        searchable = Searcheable.of(entities);
    }

    @Test
    void shouldReturnMatchingResultsWhenSearchByQuery() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .query("Alice")
            .searchableExtractor("name", TestEntity::name)
            .build();

        ArrayListTotal<TestEntity> result = searchable.search(searched);
        assertEquals(2, result.getTotal());
        assertEquals("Alice", result.getFirst().name());
    }

    @Test
    void shouldSortResultsWhenSortedAscBySingleField() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .sort(List.of("age:asc"))
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searchable.search(searched);
        assertEquals(25, result.get(0).age());
        assertEquals(30, result.get(1).age());
        assertEquals(35, result.get(2).age());
        assertEquals(40, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedDesBySingleField() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .sort(List.of("age:desc"))
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searchable.search(searched);
        assertEquals(40, result.get(0).age());
        assertEquals(35, result.get(1).age());
        assertEquals(30, result.get(2).age());
        assertEquals(25, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedByMultipleFields() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .sort(List.of("name:asc", "age:asc"))
            .sortableExtractor("name", TestEntity::name)
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searchable.search(searched);
        assertEquals("Alice", result.get(0).name());
        assertEquals(30, result.get(0).age());
        assertEquals("Alice", result.get(1).name());
        assertEquals(40, result.get(1).age());
    }

    @Test
    void shouldReturnPaginatedResultsWhenPaginationApplied() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .page(1)
            .size(2)
            .build();

        ArrayListTotal<TestEntity> result = searchable.search(searched);
        assertEquals(2, result.size());
        assertEquals(4, result.getTotal());
    }

    @Test
    void shouldFilterResultsByQueryFiltersWhenQueryFilterApplied() {
        // Given:
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .queryFilters(queryFilters)
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .build();

        // When
        ArrayListTotal<TestEntity> result = searchable.search(searched);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void shouldFilterResultsByQueryFiltersAndSearchableExtractorWhenQueryFilterAppliedAndSearchableExtractor() {
        // Given:
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .queryFilters(queryFilters)
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .searchableExtractor("age", TestEntity::age)
            .query("30")
            .build();

        // When
        ArrayListTotal<TestEntity> result = searchable.search(searched);

        // Then
        assertEquals(1, result.size());
    }

    @Test
    void shouldThrowErrorWhenUnsupportedFilterOperationProvided() {
        // Given:
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity> builder()
            .queryFilters(queryFilters)
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .build();

        // When
        InvalidQueryFiltersException queryFiltersException = assertThrows(
            InvalidQueryFiltersException.class, () -> searchable.search(searched)
        );

        // Then
        assertEquals(
            "Provided query filters are invalid: Unsupported operation for FLOW_ID: EQUALS",
            queryFiltersException.getMessage()
        );
    }

    record TestEntity(String name, int age) {
    }
}