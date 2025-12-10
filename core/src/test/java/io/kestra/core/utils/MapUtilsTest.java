package io.kestra.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapUtilsTest {
    @SuppressWarnings("unchecked")
    @Test
    void merge() {
        Map<String, Object> a = Map.of(
            "map", Map.of(
                "map_a", "a",
                "map_b", "b",
                "map_c", "c"
            ),
            "string", "a",
            "int", 1,
            "lists", Collections.singletonList(1)
        );

        Map<String, Object> b = Map.of(
            "map", Map.of(
                "map_c", "e",
                "map_d", "d"
            ),
            "string", "b",
            "float", 1F,
            "lists", Collections.singletonList(2)
        );

        Map<String, Object> merge = MapUtils.merge(a, b);

        assertThat(((Map<String, Object>) merge.get("map")).size()).isEqualTo(4);
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c")).isEqualTo("e");
        assertThat(merge.get("string")).isEqualTo("b");
        assertThat(merge.get("int")).isEqualTo(1);
        assertThat(merge.get("float")).isEqualTo(1F);
        assertThat((List<?>) merge.get("lists")).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deepMerge() {
        Map<String, Object> a = Map.of(
            "map", Map.of(
                "map_a", "a",
                "map_b", "b",
                "map_c", "c"
            ),
            "string", "a",
            "int", 1,
            "lists", Collections.singletonList(1)
        );

        Map<String, Object> b = Map.of(
            "map", Map.of(
                "map_c", "e",
                "map_d", "d"
            ),
            "string", "b",
            "float", 1F,
            "lists", Collections.singletonList(2)
        );

        Map<String, Object> merge = MapUtils.deepMerge(a, b);

        assertThat(((Map<String, Object>) merge.get("map")).size()).isEqualTo(4);
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c")).isEqualTo("e");
        assertThat(merge.get("string")).isEqualTo("b");
        assertThat(merge.get("int")).isEqualTo(1);
        assertThat(merge.get("float")).isEqualTo(1F);
        assertThat((List<?>) merge.get("lists")).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void mergeWithNull() {
        var mapWithNull = new HashMap<String, String>();
        mapWithNull.put("null", null);

        Map<String, Object> a = Map.of(
            "map", Map.of(
                "map_a", Map.of("sub", mapWithNull),
                "map_c", "c"
            )
        );

        Map<String, Object> b = Map.of(
            "map", Map.of(
                "map_c", "e",
                "map_d", "d"
            )
        );

        Map<String, Object> merge = MapUtils.merge(a, b);

        assertThat(((Map<String, Object>) merge.get("map")).size()).isEqualTo(3);
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c")).isEqualTo("e");
        assertThat(((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) merge.get("map")).get("map_a")).get("sub")).get("null")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deepMergeWithNull() {
        var mapWithNull = new HashMap<String, String>();
        mapWithNull.put("null", null);

        Map<String, Object> a = Map.of(
            "map", Map.of(
                "map_a", Map.of("sub", mapWithNull),
                "map_c", "c"
            )
        );

        Map<String, Object> b = Map.of(
            "map", Map.of(
                "map_c", "e",
                "map_d", "d"
            )
        );

        Map<String, Object> merge = MapUtils.deepMerge(a, b);

        assertThat(((Map<String, Object>) merge.get("map")).size()).isEqualTo(3);
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c")).isEqualTo("e");
        assertThat(((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) merge.get("map")).get("map_a")).get("sub")).get("null")).isNull();
    }

    @Test
    void shouldMergeWithNullableValuesGivenNullAndDuplicate() {
        @SuppressWarnings("unchecked")
        Map<String, Object> results = MapUtils.mergeWithNullableValues(
            Map.of("k1", "v1", "k2", "v1", "k3", "v1"),
            Map.of("k1", "v2"),
            Map.of("k2", "v2"),
            Map.of("k3", "v2"),
            new HashMap<>() {{
                put("k4", null);
            }}
        );

        Assertions.assertEquals(4, results.size());
        Assertions.assertEquals("v2", results.get("k1"));
        Assertions.assertEquals("v2", results.get("k2"));
        Assertions.assertEquals("v2", results.get("k3"));
        Assertions.assertNull(results.get("k4"));
    }

    @Test
    void emptyOnNull() {
        var map = MapUtils.emptyOnNull(null);
        assertThat(map).isNotNull();
        assertThat(map).isEmpty();

        map = MapUtils.emptyOnNull(Map.of("key", "value"));
        assertThat(map).isNotNull();
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    void isEmpty() {
        assertThat(MapUtils.isEmpty(null)).isTrue();
        assertThat(MapUtils.isEmpty(Collections.emptyMap())).isTrue();
        assertThat(MapUtils.isEmpty(Map.of("key", "value"))).isFalse();
    }


    @Test
    void shouldReturnMapWhenNestingMapGivenFlattenMap() {
        Map<String, Object> results = MapUtils.flattenToNestedMap(Map.of(
            "k1.k2.k3", "v1",
            "k1.k2.k4", "v2"
        ));
        Assertions.assertEquals(
            Map.of("k1", Map.of("k2", Map.of("k3", "v1", "k4", "v2"))),
            results
        );
    }

    @Test
    void shouldReturnMapAndIgnoreConflicts() {
        Map<String, Object> results = MapUtils.flattenToNestedMap(Map.of(
            "k1.k2", "v1",
            "k1.k2.k3", "v2"
        ));

        assertThat(results).hasSize(1);
        // due to ordering change on each JVM restart, the result map would be different as different entries will be skipped
    }

    @Test
    void shouldFlattenANestedMap() {
        Map<String, Object> results = MapUtils.nestedToFlattenMap(Map.of("k1",Map.of("k2", Map.of("k3", "v1")), "k4", "v2"));

        assertThat(results).hasSize(2);
        assertThat(results).containsAllEntriesOf(Map.of(
            "k1.k2.k3", "v1",
            "k4", "v2"
        ));
    }

    @Test
    void shouldFlattenANestedMapWithDuplicateKeys() {
        Map<String, Object> results =  MapUtils.nestedToFlattenMap(Map.of("k1",  Map.of("k2", Map.of("k3", "v1"), "k4", "v2")));

        assertThat(results).hasSize(2);
        assertThat(results).containsAllEntriesOf(Map.of(
            "k1.k2.k3", "v1",
            "k1.k4", "v2"
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeShouldNotDuplicateListElements() {
        Map<String, Object> first = Map.of(
            "key1", "value1",
            "key2", List.of("something", "else")
        );
        Map<String, Object> second = Map.of(
            "key2", List.of("something", "other"),
            "key3", "value3"
        );

        Map<String, Object> results = MapUtils.merge(first, second);

        assertThat(results).hasSize(3);
        List<String> list = (List<String>) results.get("key2");
        assertThat(list).hasSize(3);
    }
}