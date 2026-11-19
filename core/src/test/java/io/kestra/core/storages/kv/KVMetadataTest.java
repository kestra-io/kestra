package io.kestra.core.storages.kv;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KVMetadataTest {

    // PT9223372036854775807S overflows the epoch second itself,
    // P999999999999D passes Instant.MAX without overflowing it,
    // P3000000D yields an Instant around year 10240 beyond year 9999.
    @ParameterizedTest
    @ValueSource(strings = { "PT9223372036854775807S", "P999999999999D", "P3000000D" })
    void shouldRejectATtlWhoseExpirationIsBeyondTheLatestInstant(String ttl) {
        assertThatThrownBy(() -> new KVMetadata(null, Duration.parse(ttl)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void shouldAcceptAndRejectExpirationDateBoundary() {
        assertThatNoException().isThrownBy(() -> new KVMetadata(null, KVMetadata.MAX_EXPIRATION_DATE));

        Instant beyondMax = KVMetadata.MAX_EXPIRATION_DATE.plusMillis(1);
        assertThatThrownBy(() -> new KVMetadata(null, beyondMax))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too large");
    }
}
