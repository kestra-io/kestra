package io.kestra.repository.mysql.migration;

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
 * MySQL-specific integration test for {@link V2_0_23FixDatetimeOffsetMigration}.
 *
 * <p>
 * Only offsets with a non-zero minutes part were corrupted; a whole-hour offset passes either way
 * because the character the extraction dropped is a {@code 0}. A negative offset is the damaging
 * case — it moves {@code end_date} backwards, expiring a live window early.
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class MysqlV2_0_23FixDatetimeOffsetMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));

    /** Read as text so the assertions cannot be perturbed by a client-side timezone conversion. */
    private static final Field<String> START_DATE = DSL.field("CAST(`start_date` AS CHAR)", String.class);
    private static final Field<String> END_DATE = DSL.field("CAST(`end_date` AS CHAR)", String.class);

    private static final String UTC_KEY = "fix-datetime-offset-utc";
    private static final String PARIS_KEY = "fix-datetime-offset-paris";
    private static final String KOLKATA_KEY = "fix-datetime-offset-kolkata";
    private static final String NEWFOUNDLAND_KEY = "fix-datetime-offset-newfoundland";

    /** Every window below spans the same instants: 10:00:00Z to 11:00:00Z on 2024-06-01. */
    private static final String EXPECTED_START = "2024-06-01 10:00:00.000000";
    private static final String EXPECTED_END = "2024-06-01 11:00:00.000000";

    /** The pre-fix expression, restored so the migration has something wrong to recompute. */
    private static final String PRE_FIX_END_DATE = """
        ALTER TABLE multipleconditions MODIFY COLUMN `end_date` DATETIME(6) GENERATED ALWAYS AS (
            IF(
                SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end'), LENGTH(value ->> '$.end')) = 'Z',
                STR_TO_DATE(value ->> '$.end', '%Y-%m-%dT%H:%i:%s.%fZ'),
                CONVERT_TZ(
                    STR_TO_DATE(SUBSTRING(value ->> '$.end', 1, LENGTH(value ->> '$.end') - 6), '%Y-%m-%dT%H:%i:%s.%f'),
                    SUBSTRING(value ->> '$.end', LENGTH(value ->> '$.end') - 5, 5),
                    'UTC'
                )
            )
        ) STORED NOT NULL""";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_23FixDatetimeOffsetMigration migration;

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("multipleconditions"))
                .where(KEY.in(UTC_KEY, PARIS_KEY, KOLKATA_KEY, NEWFOUNDLAND_KEY))
                .execute()
        );
    }

    /** Guarantees the fixed definition is in place even if a test fails after reverting it. */
    @AfterEach
    void restoreFixedColumnDefinition() throws Exception {
        migration.migrate();
    }

    @Test
    void shouldResolveOffsetsWithNonZeroMinutesToUtc() throws Exception {
        // Given
        migration.migrate();

        // When
        insert(UTC_KEY, "2024-06-01T10:00:00.000Z", "2024-06-01T11:00:00.000Z");
        insert(PARIS_KEY, "2024-06-01T12:00:00.000+02:00", "2024-06-01T13:00:00.000+02:00");
        insert(KOLKATA_KEY, "2024-06-01T15:30:00.000+05:30", "2024-06-01T16:30:00.000+05:30");
        insert(NEWFOUNDLAND_KEY, "2024-06-01T06:30:00.000-03:30", "2024-06-01T07:30:00.000-03:30");

        // Then
        for (String key : new String[]{UTC_KEY, PARIS_KEY, KOLKATA_KEY, NEWFOUNDLAND_KEY}) {
            assertThat(read(START_DATE, key)).as("start_date of %s", key).isEqualTo(EXPECTED_START);
            assertThat(read(END_DATE, key)).as("end_date of %s", key).isEqualTo(EXPECTED_END);
        }
    }

    @Test
    void shouldRecomputeRowsStoredUnderThePreFixExpression() throws Exception {
        // Given a row written while end_date still used the truncated offset
        execute(PRE_FIX_END_DATE);
        insert(NEWFOUNDLAND_KEY, "2024-06-01T06:30:00.000-03:30", "2024-06-01T07:30:00.000-03:30");
        assertThat(read(END_DATE, NEWFOUNDLAND_KEY))
            .as("the pre-fix expression reads -03:30 as -03:03, expiring this window 27 minutes early")
            .isEqualTo("2024-06-01 10:33:00.000000");

        // When
        migration.migrate();

        // Then the already-stored row has been recomputed, not just newly inserted ones
        assertThat(read(END_DATE, NEWFOUNDLAND_KEY)).isEqualTo(EXPECTED_END);
    }

    private void execute(String sql) {
        dslContextWrapper.transaction(configuration -> DSL.using(configuration).execute(sql));
    }

    private void insert(String key, String start, String end) {
        String valueJson = """
            {"namespace":"io.kestra.tests","flowId":"migration","conditionId":"%s",\
            "start":"%s","end":"%s"}""".formatted(key, start, end);

        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("multipleconditions"))
                .set(KEY, (Object) key)
                .set(VALUE, (Object) valueJson)
                .execute()
        );
    }

    private String read(Field<String> field, String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(field)
                .from(DSL.table("multipleconditions"))
                .where(KEY.eq(key))
                .fetchOne(field)
        );
    }
}
