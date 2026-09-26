package io.kestra.runner.mysql;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlExecutionDelayStateStoreTest {

    private final MysqlExecutionDelayStateStore store = new MysqlExecutionDelayStateStore(null);

    @Test
    void shouldConvertGivenInstantToUtcWallClock() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        assertThat(store.getNow(now)).isEqualTo(LocalDateTime.parse("2026-01-01T10:00:00"));
    }
}
