package io.kestra.jdbc.runner;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the default bind of the delay poll: a value without a timezone is re-read in the session
 * timezone by PostgreSQL, which shifted the due-date predicate and left executions stuck in RETRYING
 * (kestra-io/kestra#14056). Dialects whose column cannot hold an offset override this.
 */
class AbstractJdbcExecutionDelayStateStoreTest {

    private final AbstractJdbcExecutionDelayStateStore store = new AbstractJdbcExecutionDelayStateStore(null) {
    };

    @Test
    void shouldNotBindZoneLessLocalDateTime() {
        assertThat(store.getNow(Instant.now()))
            .isNotInstanceOf(LocalDateTime.class);
    }

    @Test
    void shouldDenoteGivenInstant() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        assertThat(asInstant(store.getNow(now))).isEqualTo(now);
    }

    private static Instant asInstant(Temporal temporal) {
        if (temporal instanceof Instant instant) {
            return instant;
        }
        if (temporal instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (temporal instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        throw new AssertionError("unexpected bind type " + temporal.getClass());
    }
}
