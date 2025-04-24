package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlyDelegatingMapTest {

    @Test
    void readOnlyDelegatingMap() {
        var map = new TestMap();

        assertThat(map).hasSize(1);
        assertThat(map.isEmpty()).isFalse();
        assertThat(map.containsKey("key")).isTrue();
        assertThat(map.containsValue("value")).isTrue();
        assertThat(map.keySet()).hasSize(1);
        assertThat(map.get("key")).isEqualTo("value");
        assertThat(map.values()).hasSize(1);
        assertThat(map.entrySet()).hasSize(1);

        assertThrows(UnsupportedOperationException.class, () -> map.put("key", "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of("key", "value")));
        assertThrows(UnsupportedOperationException.class, () -> map.clear());
        assertThrows(UnsupportedOperationException.class, () -> map.remove("key"));
    }

    private static class TestMap extends ReadOnlyDelegatingMap<String, String> {

        @Override
        protected Map<String, String> getDelegate() {
            return new HashMap<>(Map.of("key", "value"));
        }
    }

}