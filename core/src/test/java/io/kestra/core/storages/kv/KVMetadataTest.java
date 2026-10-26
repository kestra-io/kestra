package io.kestra.core.storages.kv;

import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KVMetadataTest {

    // PT9223372036854775807S overflows the epoch second itself, P999999999999D passes Instant.MAX without overflowing it.
    @ParameterizedTest
    @ValueSource(strings = { "PT9223372036854775807S", "P999999999999D" })
    void shouldRejectATtlWhoseExpirationIsBeyondTheLatestInstant(String ttl) {
        assertThatThrownBy(() -> new KVMetadata(null, Duration.parse(ttl)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too large");
    }
}
