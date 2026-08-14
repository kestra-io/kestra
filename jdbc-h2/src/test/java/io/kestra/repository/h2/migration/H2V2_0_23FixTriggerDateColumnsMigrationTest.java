package io.kestra.repository.h2.migration;

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

/**
 * H2-specific integration test for {@link V2_0_23FixTriggerDateColumnsMigration}.
 *
 * <p>
 * The width dependency only shows on a server whose timezone is not UTC, so the session timezone
 * is forced — otherwise these would pass unchanged on a UTC CI machine even with the fix reverted.
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class H2V2_0_23FixTriggerDateColumnsMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));

    /** Read as text so the assertions cannot be perturbed by a client-side timezone conversion. */
    private static final Field<String> NEXT_EVALUATION_DATE =
        DSL.field("CAST(\"next_evaluation_date\" AS VARCHAR)", String.class);
    private static final Field<String> LAST_TRIGGERED_DATE =
        DSL.field("CAST(\"last_triggered_date\" AS VARCHAR)", String.class);

    private static final String LEGACY_KEY = "fix-trigger-date-columns-legacy";
    private static final String CURRENT_KEY = "fix-trigger-date-columns-current";

    /** 3 fractional digits, so LEFT(value, 26) leaves the 'Z' in place. */
    private static final String LEGACY_VALUE = """
        {"namespace":"io.kestra.tests","flowId":"migration","triggerId":"legacy","disabled":false,\
        "nextEvaluationDate":"2024-06-01T10:00:00.000Z","lastTriggeredDate":"2024-06-01T10:00:00.000Z"}""";

    /** Current JdbcMapper format: 6 fractional digits. */
    private static final String CURRENT_VALUE = """
        {"namespace":"io.kestra.tests","flowId":"migration","triggerId":"current","disabled":false,\
        "nextEvaluationDate":"2024-06-01T10:00:00.123456Z","lastTriggeredDate":"2024-06-01T10:00:00.123456Z"}""";

    /** The pre-fix expression, restored so the migration has something wrong to recompute. */
    private static final String PRE_FIX_NEXT_EVALUATION_DATE = """
        ALTER TABLE triggers ALTER COLUMN "next_evaluation_date" TIMESTAMP \
        GENERATED ALWAYS AS (CAST(LEFT(JQ_STRING("value", '.nextEvaluationDate'), 26) AS TIMESTAMP))""";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_23FixTriggerDateColumnsMigration migration;

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("triggers"))
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
    void shouldResolveThreeDigitTimestampToUtcWhenServerIsNotUtc() throws Exception {
        // Given
        migration.migrate();

        // When
        insertWithSessionTimeZone(LEGACY_KEY, LEGACY_VALUE);

        // Then — 2024-06-01T10:00:00Z, not shifted by the +05:30 session offset
        assertThat(read(NEXT_EVALUATION_DATE, LEGACY_KEY)).isEqualTo("2024-06-01 10:00:00");
        assertThat(read(LAST_TRIGGERED_DATE, LEGACY_KEY)).isEqualTo("2024-06-01 10:00:00");
    }

    @Test
    void shouldKeepMicrosecondPrecisionForCurrentFormat() throws Exception {
        // Given
        migration.migrate();

        // When
        insertWithSessionTimeZone(CURRENT_KEY, CURRENT_VALUE);

        // Then
        assertThat(read(NEXT_EVALUATION_DATE, CURRENT_KEY)).isEqualTo("2024-06-01 10:00:00.123456");
        assertThat(read(LAST_TRIGGERED_DATE, CURRENT_KEY)).isEqualTo("2024-06-01 10:00:00.123456");
    }

    @Test
    void shouldRecomputeRowsStoredUnderThePreFixExpression() throws Exception {
        // Given a row written while next_evaluation_date still depended on the 27-character width
        execute(PRE_FIX_NEXT_EVALUATION_DATE);
        insertWithSessionTimeZone(LEGACY_KEY, LEGACY_VALUE);
        assertThat(read(NEXT_EVALUATION_DATE, LEGACY_KEY))
            .as("the pre-fix expression must shift the value by the +05:30 session offset")
            .isEqualTo("2024-06-01 15:30:00");

        // When
        migration.migrate();

        // Then the already-stored row has been recomputed, not just newly inserted ones
        assertThat(read(NEXT_EVALUATION_DATE, LEGACY_KEY)).isEqualTo("2024-06-01 10:00:00");
    }

    private void execute(String sql) {
        dslContextWrapper.transaction(configuration -> DSL.using(configuration).execute(sql));
    }

    /**
     * Inserts under a non-UTC session timezone, which is what makes the width dependency
     * observable. The generated columns are computed and stored at insert time, so the timezone is
     * restored before reading. jOOQ binds one connection for the lambda and cannot hand it to
     * another thread while it is checked out, so the {@code finally} keeps the setting from leaking
     * back into the pool even if the insert throws.
     */
    private void insertWithSessionTimeZone(String key, String valueJson) {
        dslContextWrapper.transaction(configuration -> {
            var context = DSL.using(configuration);
            context.execute("SET TIME ZONE '+05:30'");
            try {
                context.insertInto(DSL.table("triggers"))
                    .set(KEY, (Object) key)
                    .set(VALUE, (Object) valueJson)
                    .execute();
            } finally {
                context.execute("SET TIME ZONE LOCAL");
            }
        });
    }

    private String read(Field<String> field, String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(field)
                .from(DSL.table("triggers"))
                .where(KEY.eq(key))
                .fetchOne(field)
        );
    }
}
