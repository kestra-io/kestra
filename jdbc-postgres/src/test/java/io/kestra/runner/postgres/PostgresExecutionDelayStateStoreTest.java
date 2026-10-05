package io.kestra.runner.postgres;

import java.time.Instant;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresExecutionDelayStateStoreTest {

	@Test
	void shouldBindTheDelayCutoffAsTimestampWithTimeZone() {
		Instant cutoff = Instant.parse("2031-01-15T10:00:00Z");
		var context = DSL.using(SQLDialect.POSTGRES);
		var predicate = DSL.field(DSL.quotedName("date")).lessOrEqual(cutoff);
		var query = context.selectOne().where(predicate);
		var cutoffParameter = query.getParams().values().stream()
			.filter(parameter -> cutoff.equals(parameter.getValue()))
			.findFirst()
			.orElseThrow();

		assertThat(cutoffParameter.getValue()).isEqualTo(cutoff);
		assertThat(context.renderInlined(predicate)).contains("timestamp with time zone");
	}
}
