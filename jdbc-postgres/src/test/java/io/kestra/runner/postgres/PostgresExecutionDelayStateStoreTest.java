package io.kestra.runner.postgres;

import java.time.Instant;
import java.time.temporal.Temporal;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresExecutionDelayStateStoreTest {

    // The assertion has to read the cutoff from getNow(now), the value processExpired binds.
    private static class ProbeStore extends PostgresExecutionDelayStateStore {
        ProbeStore() {
            super(null);
        }

        Temporal cutoff(Instant now) {
            return getNow(now);
        }
    }

    @Test
    void shouldBindTheDelayCutoffAsTimestampWithTimeZone() {
        Instant now = Instant.parse("2031-01-15T10:00:00Z");

        var cutoff = new ProbeStore().cutoff(now);
        assertThat(cutoff).isEqualTo(now);

        var context = DSL.using(SQLDialect.POSTGRES);
        var predicate = DSL.field(DSL.quotedName("date")).lessOrEqual(cutoff);

        assertThat(context.renderInlined(predicate)).contains("timestamp with time zone");
    }

    @Test
    void shouldNotConvertTheDelayCutoffToANaiveLocalDateTime() {
        var context = DSL.using(SQLDialect.POSTGRES);
        var naive = DSL.field(DSL.quotedName("date"))
            .lessOrEqual(java.time.LocalDateTime.of(2031, 1, 15, 10, 0));

        assertThat(context.renderInlined(naive)).doesNotContain("timestamp with time zone");
    }
}