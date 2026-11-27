package io.kestra.repository.postgres.migration;

import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class PostgresV2_1_05QueueOffsetBigintMigrationTest {

    @Inject
    QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    V2_1_05QueueOffsetBigintMigration migration;

    @Test
    void queueOffsetSequenceAndColumnAreBigintAndCanExceed32BitInt() throws Exception {
        migration.migrate();

        queueJdbcDataSourceProvider.wrapper().transaction(configuration -> {
            var dsl = DSL.using(configuration);

            // 1. Verify column type is bigint
            String columnDataType = dsl.resultQuery(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'queues' AND column_name = 'offset' " +
                "ORDER BY (table_schema = current_schema()) DESC"
            ).fetchAny(0, String.class);

            assertThat(columnDataType).isNotNull();
            assertThat(columnDataType).isEqualToIgnoringCase("bigint");

            // 2. Verify sequence accepts values >= 2,147,483,647 (Integer.MAX_VALUE)
            dsl.execute("SELECT setval('queues_offset_seq', 2147483647)");
            Long nextVal = dsl.resultQuery("SELECT nextval('queues_offset_seq')")
                .fetchOne(0, Long.class);

            assertThat(nextVal).isEqualTo(2147483648L);
        });
    }

    @Test
    void migrationIsIdempotent() {
        assertThatCode(() -> {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();
    }
}
