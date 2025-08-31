package io.kestra.webserver.utils;

import io.kestra.core.repositories.ArrayListTotal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearcheableTest {
    private Searcheable<TestEntity> searcheable;

    @BeforeEach
    void setUp() {
        List<TestEntity> entities = List.of(
            new TestEntity("Alice", 30),
            new TestEntity("Bob", 25),
            new TestEntity("Charlie", 35),
            new TestEntity("Alice", 40)
        );
        searcheable = Searcheable.of(entities);
    }

    @Test
    void shouldReturnMatchingResultsWhenSearchByQuery() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity>builder()
            .query("Alice")
            .searchableExtractor("name", TestEntity::name)
            .build();

        ArrayListTotal<TestEntity> result = searcheable.search(searched);
        assertEquals(2, result.getTotal());
        assertEquals("Alice", result.getFirst().name());
    }

    @Test
    void shouldSortResultsWhenSortedAscBySingleField() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity>builder()
            .sort(List.of("age:asc"))
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searcheable.search(searched);
        assertEquals(25, result.get(0).age());
        assertEquals(30, result.get(1).age());
        assertEquals(35, result.get(2).age());
        assertEquals(40, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedDesBySingleField() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity>builder()
            .sort(List.of("age:desc"))
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searcheable.search(searched);
        assertEquals(40, result.get(0).age());
        assertEquals(35, result.get(1).age());
        assertEquals(30, result.get(2).age());
        assertEquals(25, result.get(3).age());
    }

    @Test
    void shouldSortResultsWhenSortedByMultipleFields() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity>builder()
            .sort(List.of("name:asc", "age:asc"))
            .sortableExtractor("name", TestEntity::name)
            .sortableExtractor("age", TestEntity::age)
            .build();

        ArrayListTotal<TestEntity> result = searcheable.search(searched);
        assertEquals("Alice", result.get(0).name());
        assertEquals(30, result.get(0).age());
        assertEquals("Alice", result.get(1).name());
        assertEquals(40, result.get(1).age());
    }

    @Test
    void shouldReturnPaginatedResultsWhenPaginationApplied() {
        Searcheable.Searched<TestEntity> searched = Searcheable.Searched.<TestEntity>builder()
            .page(1)
            .size(2)
            .build();

        ArrayListTotal<TestEntity> result = searcheable.search(searched);
        assertEquals(2, result.size());
        assertEquals(4, result.getTotal());
    }

    record TestEntity(String name, int age) {
    }
}