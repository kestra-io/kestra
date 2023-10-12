package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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

        assertThat(((Map<String, Object>) merge.get("map")).size(), is(4));
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c"), is("e"));
        assertThat(merge.get("string"), is("b"));
        assertThat(merge.get("int"), is(1));
        assertThat(merge.get("float"), is(1F));
        assertThat((List<?>) merge.get("lists"), hasSize(2));
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

        assertThat(((Map<String, Object>) merge.get("map")).size(), is(3));
        assertThat(((Map<String, Object>) merge.get("map")).get("map_c"), is("e"));
        assertThat(((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) merge.get("map")).get("map_a")).get("sub")).get("null"), nullValue());
    }
}