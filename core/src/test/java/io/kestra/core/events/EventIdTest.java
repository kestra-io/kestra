package io.kestra.core.events;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class EventIdTest {
    @Test
    void shouldCreateNewEventIdGivenFactoryMethod() {
        // Given / When
        EventId id = EventId.create();

        // Then
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
        assertThat(id.toString()).isEqualTo(id.value().toString());
    }

    @Test
    void shouldFailGivenNullUuid() {
        // When / Then
        assertThatThrownBy(() -> new EventId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldCreateGivenValidUuidString() {
        // Given
        String uuidStr = UUID.randomUUID().toString();

        // When
        EventId id = EventId.fromString(uuidStr);

        // Then
        assertThat(id.value().toString()).isEqualTo(uuidStr);
    }

    @Test
    void shouldCompareCorrectlyGivenChronologicalOrdering() {
        // Given two generated IDs (UUIDv7 ensures creation order is chronological)
        EventId first = EventId.create();
        EventId second = EventId.create();

        // When / Then
        assertThat(first.compareTo(second)).isLessThan(0);
        assertThat(second.compareTo(first)).isGreaterThan(0);

        assertThat(second.isNewerThan(first)).isTrue();
        assertThat(first.isOlderThan(second)).isTrue();
    }

    @Test
    void shouldBeEqualGivenSameUnderlyingUuid() {
        // Given
        UUID uuid = UUID.randomUUID();

        EventId id1 = new EventId(uuid);
        EventId id2 = new EventId(uuid);

        // When / Then
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.compareTo(id2)).isZero();
        assertThat(id1.isNewerThan(id2)).isFalse();
        assertThat(id1.isOlderThan(id2)).isFalse();
    }

    @Test
    void shouldReturnStringRepresentationGivenToStringCall() {
        // Given
        UUID uuid = UUID.randomUUID();
        EventId id = new EventId(uuid);

        // When
        String str = id.toString();

        // Then
        assertThat(str).isEqualTo(uuid.toString());
    }

    @Test
    void shouldSerdesToUUIDString() throws JsonProcessingException {
        // Given
        EventId id = EventId.create();

        // When/Then
        String serialized = JacksonMapper.ofJson().writeValueAsString(id);
        assertThat(serialized).isEqualTo("\"" + id.value().toString() + "\"");

        // When/Then
        EventId deserialized = JacksonMapper.ofJson().readValue(serialized, EventId.class);
        assertThat(deserialized).isEqualTo(id);
    }
}