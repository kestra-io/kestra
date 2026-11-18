package io.kestra.runner.postgres;

import java.time.Instant;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresExecutionDelayStateStoreCutoffBindTest extends PostgresExecutionDelayStateStore {

    PostgresExecutionDelayStateStoreCutoffBindTest() {
        super(null);
    }

    @Test
    void shouldBindTheDelayCutoffAsTimestampWithTimeZone() {
        Instant now = Instant.parse("2031-01-15T10:00:00Z");

        var cutoff = getNow(now);
        assertThat(cutoff).isInstanceOf(Instant.class);
        assertThat(cutoff).isEqualTo(now);

        var context = DSL.using(SQLDialect.POSTGRES);

        assertThat(context.renderInlined(expiredCondition(now))).contains("timestamp with time zone");
    }
}
