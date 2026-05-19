package io.kestra.webserver.utils;

import java.util.List;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.ArrayListTotal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchableTest {
    private List<TestEntity> entities;

    @BeforeEach
    void setUp() {
        entities = List.of(
            new TestEntity("Alice", 30),
            new TestEntity("Bob", 25),
            new TestEntity("Charlie", 35),
            new TestEntity("Alice", 40)
        );
    }

    @Test
    void shouldReturnMatchingResultsWhenSearchByQuery() {
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .searchableExtractor("name", TestEntity::name)
            .build()
            .filter(entities, 1, 100, null, null, "Alice");

        assertEquals(2, result.getTotal());
        assertEquals("Alice", result.getFirst().name());
    }

    @Test
    void shouldSortResultsWhenSortedAscBySingleField() {
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .sortableExtractor("age", TestEntity::age)
            .build()
            .filter(entities, 1, 100, List.of("age:asc"), null, null);

        assertEquals(25, result.get(0).age());
        assertEquals(30, result.get(1).age());
        assertEquals(35, result.get(2).age());
        assertEquals(40, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedDesBySingleField() {
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .sortableExtractor("age", TestEntity::age)
            .build()
            .filter(entities, 1, 100, List.of("age:desc"), null, null);

        assertEquals(40, result.get(0).age());
        assertEquals(35, result.get(1).age());
        assertEquals(30, result.get(2).age());
        assertEquals(25, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedByMultipleFields() {
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .sortableExtractor("name", TestEntity::name)
            .sortableExtractor("age", TestEntity::age)
            .build()
            .filter(entities, 1, 100, List.of("name:asc", "age:asc"), null, null);

        assertEquals("Alice", result.get(0).name());
        assertEquals(30, result.get(0).age());
        assertEquals("Alice", result.get(1).name());
        assertEquals(40, result.get(1).age());
    }

    @Test
    void shouldReturnPaginatedResultsWhenPaginationApplied() {
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .build()
            .filter(entities, 1, 2, null, null, null);

        assertEquals(2, result.size());
        assertEquals(4, result.getTotal());
    }

    @Test
    void shouldFilterResultsByQueryFiltersWhenQueryFilterApplied() {
        // Given
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        // When
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .build()
            .filter(entities, 1, 100, null, queryFilters, null);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void shouldFilterResultsByQueryFiltersAndSearchableExtractorWhenQueryFilterAppliedAndSearchableExtractor() {
        // Given
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        // When
        ArrayListTotal<TestEntity> result = Searchable.<TestEntity>builder()
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .searchableExtractor("age", TestEntity::age)
            .build()
            .filter(entities, 1, 100, null, queryFilters, "30");

        // Then
        assertEquals(1, result.size());
    }

    @Test
    void shouldThrowErrorWhenUnsupportedFilterOperationProvided() {
        // Given
        List<QueryFilter> queryFilters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .value("Alice")
                .operation(QueryFilter.Op.EQUALS)
                .build()
        );

        Searchable<TestEntity> searchable = Searchable.<TestEntity>builder()
            .searchableQueryFilterExtractor(
                QueryFilter.Field.QUERY,
                QueryFilter.Op.EQUALS,
                (testEntity, value) -> testEntity.name().equals(value)
            )
            .build();

        // When / Then
        InvalidQueryFiltersException queryFiltersException = assertThrows(
            InvalidQueryFiltersException.class,
            () -> searchable.filter(entities, 1, 100, null, queryFilters, null)
        );

        assertEquals(
            "Provided query filters are invalid: Unsupported operation for FLOW_ID: EQUALS",
            queryFiltersException.getMessage()
        );
    }

    record TestEntity(String name, int age) {
    }
}
