package io.kestra.runner.postgres;

import java.time.Instant;
import java.time.temporal.Temporal;

import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL re-reads a zone-less due-date bind in the session timezone, which pgjdbc takes from the
 * JVM default, so retries waited for hours on any non-UTC host (kestra-io/kestra#14056). Rendering the
 * poll's predicate keeps the assertion deterministic and database-free.
 */
class PostgresExecutionDelayStateStoreTest {

    private static final Table<?> EXECUTION_DELAY = DSL.table(DSL.quotedName("executordelayed"));
    private static final Field<Object> DATE = DSL.field(DSL.quotedName("date"));

    @Test
    void shouldBindDueDateAsTimestampWithTimeZone() {
        Temporal now = new ExposedStore().now(Instant.parse("2026-01-01T10:00:00Z"));

        String poll = DSL.using(SQLDialect.POSTGRES).renderInlined(
            DSL.select(DATE).from(EXECUTION_DELAY).where(DATE.lessOrEqual(now)));

        assertThat(poll).contains("timestamp with time zone");
    }

    // getNow is protected in io.kestra.jdbc.runner, so a subclass is the only access from this package.
    private static final class ExposedStore extends PostgresExecutionDelayStateStore {

        ExposedStore() {
            super(null);
        }

        Temporal now(Instant instant) {
            return getNow(instant);
        }
    }
}
