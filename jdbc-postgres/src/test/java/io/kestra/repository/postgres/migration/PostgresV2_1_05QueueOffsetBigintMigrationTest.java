package io.kestra.repository.postgres.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
class PostgresV2_1_05QueueOffsetBigintMigrationTest {

    @Inject
    QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    V2_1_05QueueOffsetBigintMigration migration;

    @Test
    void queueOffsetSequenceAndColumnAreBigintAndCanExceed32BitInt() throws Exception {
        // Run migration (idempotent)
        migration.migrate();

        DataSource dataSource = queueJdbcDataSourceProvider.dataSource();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // 1. Verify column type is bigint (or INT8)
            String columnTypeName = null;
            try (ResultSet columns = metaData.getColumns(null, null, "queues", "offset")) {
                if (columns.next()) {
                    columnTypeName = columns.getString("TYPE_NAME");
                }
            }
            if (columnTypeName == null) {
                try (ResultSet columns = metaData.getColumns(null, null, "QUEUES", "OFFSET")) {
                    if (columns.next()) {
                        columnTypeName = columns.getString("TYPE_NAME");
                    }
                }
            }
            assertThat(columnTypeName).isNotNull();
            assertThat(columnTypeName.toLowerCase()).isIn("int8", "bigint");

            // 2. Verify sequence accepts values >= 2,147,483,647 (Integer.MAX_VALUE)
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT setval('queues_offset_seq', 2147483647)");
                try (ResultSet rs = statement.executeQuery("SELECT nextval('queues_offset_seq')")) {
                    assertThat(rs.next()).isTrue();
                    long nextVal = rs.getLong(1);
                    assertThat(nextVal).isEqualTo(2147483648L);
                }
            }
        }
    }

    @Test
    void migrationIsIdempotent() {
        assertThatCode(() -> {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();
    }
}
