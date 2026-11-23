package io.kestra.repository.h2.migration;

import java.util.Objects;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/** Inserts under a +05:30 session zone so a zone-shifted value cannot pass as UTC. */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class H2V2_1_10FixExecutionDelayDateMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));

    private static final Field<String> DATE = DSL.field("CAST(\"date\" AS VARCHAR)", String.class);

    private static final String LEGACY_KEY = "fix-execution-delay-date-legacy";
    private static final String CURRENT_KEY = "fix-execution-delay-date-current";

    private static final String LEGACY_VALUE = """
        {"taskRunId":"migration","executionId":"migration","date":"2031-01-15T10:00:00.000Z","state":"RUNNING","delayType":"RESUME_FLOW"}""";

    private static final String CURRENT_VALUE = """
        {"taskRunId":"migration","executionId":"migration","date":"2031-01-15T10:00:00.123456Z","state":"RUNNING","delayType":"RESUME_FLOW"}""";

    /** The pre-fix expression, restored so the migration has something wrong to recompute. */
    private static final String PRE_FIX_DATE = """
        ALTER TABLE executordelayed ALTER COLUMN "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS \
        (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX'))""";

    private final JooqDSLContextWrapper dslContextWrapper;

    private final V2_1_10FixExecutionDelayDateMigration migration;

    @Inject
    H2V2_1_10FixExecutionDelayDateMigrationTest(JooqDSLContextWrapper dslContextWrapper, V2_1_10FixExecutionDelayDateMigration migration) {
        this.dslContextWrapper = Objects.requireNonNull(dslContextWrapper);
        this.migration = Objects.requireNonNull(migration);
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("executordelayed"))
                .where(KEY.in(LEGACY_KEY, CURRENT_KEY))
                .execute()
        );
    }

    /** Guarantees the fixed definition is in place even if a test fails after reverting it. */
    @AfterEach
    void restoreFixedColumnDefinition() throws Exception {
        migration.migrate();
    }

    @Test
    void shouldResolveThreeDigitTimestampToUtcWhenTheServerIsNotUtc() throws Exception {
        migration.migrate();

        insertWithSessionTimeZone(LEGACY_KEY, LEGACY_VALUE);

        // 2031-01-15T10:00:00Z, not shifted by the +05:30 session offset
        assertThat(read(DATE, LEGACY_KEY)).isEqualTo("2031-01-15 10:00:00");
    }

    @Test
    void shouldKeepMicrosecondPrecisionWhenTheValueUsesSixFractionalDigits() throws Exception {
        migration.migrate();

        insertWithSessionTimeZone(CURRENT_KEY, CURRENT_VALUE);

        assertThat(read(DATE, CURRENT_KEY)).isEqualTo("2031-01-15 10:00:00.123456");
    }

    @Test
    void shouldRecomputeStoredRowsWhenTheColumnStillUsesThePreFixExpression() throws Exception {
        execute(PRE_FIX_DATE);
        insertWithSessionTimeZone(LEGACY_KEY, LEGACY_VALUE);
        assertThat(read(DATE, LEGACY_KEY))
            .as("the pre-fix expression must shift the value by the +05:30 session offset")
            .isEqualTo("2031-01-15 15:30:00");

        migration.migrate();

        assertThat(read(DATE, LEGACY_KEY)).isEqualTo("2031-01-15 10:00:00");
    }

    private void execute(String sql) {
        dslContextWrapper.transaction(configuration -> DSL.using(configuration).execute(sql));
    }

    /** Inserts under a non-UTC session zone; the finally restores the URL's UTC session zone. */
    private void insertWithSessionTimeZone(String key, String valueJson) {
        dslContextWrapper.transaction(configuration ->
        {
            var context = DSL.using(configuration);
            context.execute("SET TIME ZONE '+05:30'");
            try {
                context.insertInto(DSL.table("executordelayed"))
                    .set(KEY, (Object) key)
                    .set(VALUE, (Object) valueJson)
                    .execute();
            } finally {
                context.execute("SET TIME ZONE 'UTC'");
            }
        });
    }

    private String read(Field<String> field, String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(field)
                .from(DSL.table("executordelayed"))
                .where(KEY.eq(key))
                .fetchOne(field)
        );
    }
}
