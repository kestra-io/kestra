package io.kestra.webserver.cache;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBytesCacheWeigherTest {

    private final JsonBytesCacheWeigher weigher = new JsonBytesCacheWeigher();

    @Test
    void shouldWeighEntriesByTheirSerializedJsonSizeWhenValueIsSerializable() {
        // Given
        Map<String, String> small = Map.of("icon", "a".repeat(100));
        Map<String, String> large = Map.of("icon", "a".repeat(100_000));

        // When
        int smallWeight = weigher.weigh("key", small);
        int largeWeight = weigher.weigh("key", large);

        // Then: weights track the serialized byte size, not a per-entry constant
        assertThat(smallWeight).isGreaterThan(100).isLessThan(1_000);
        assertThat(largeWeight).isGreaterThan(100_000);
    }

    @Test
    void shouldFallBackToFixedWeightWhenValueCannotBeSerialized() {
        // Given: a value whose serialization fails
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getBroken() {
                throw new IllegalStateException("boom");
            }
        };

        // When
        int weight = weigher.weigh("key", unserializable);

        // Then
        assertThat(weight).isEqualTo(JsonBytesCacheWeigher.FALLBACK_WEIGHT);
    }
}
