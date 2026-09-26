package io.kestra.runner.h2;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 keeps the delay date in a naive TIMESTAMP holding UTC, so an instant bind is reinterpreted in the
 * session timezone and the due-date comparison becomes offset-dependent (kestra-io/kestra#14056).
 */
class H2ExecutionDelayStateStoreTest {

    private final H2ExecutionDelayStateStore store = new H2ExecutionDelayStateStore(null);

    @Test
    void shouldConvertGivenInstantToUtcWallClock() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        assertThat(store.getNow(now)).isEqualTo(LocalDateTime.parse("2026-01-01T10:00:00"));
    }
}
