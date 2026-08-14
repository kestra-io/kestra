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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2-specific integration test for {@link V2_0_24MultipleConditionsDateWidthMigration}.
 *
 * <p>
 * This migration is the prerequisite for moving the {@code ZonedDateTime} serializer to six
 * fractional digits: before it, {@code PARSEDATETIME(..., 'SSSXXX')} rejected anything but three,
 * and both columns are {@code NOT NULL}.
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class H2V2_0_24MultipleConditionsDateWidthMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));

    /** Read as text so the assertions cannot be perturbed by a client-side timezone conversion. */
    private static final Field<String> START_DATE = DSL.field("CAST(\"start_date\" AS VARCHAR)", String.class);
    private static final Field<String> END_DATE = DSL.field("CAST(\"end_date\" AS VARCHAR)", String.class);

    private static final String LEGACY_KEY = "date-width-legacy";
    private static final String CURRENT_KEY = "date-width-current";
    private static final String PARIS_KEY = "date-width-paris";
    private static final String KOLKATA_KEY = "date-width-kolkata";
    private static final String NEWFOUNDLAND_KEY = "date-width-newfoundland";
    private static final String NO_FRACTION_KEY = "date-width-no-fraction";

    /** Every window below spans the same instants: 10:00:00Z to 11:00:00Z on 2024-06-01. */
    private static final String EXPECTED_START = "2024-06-01 10:00:00";
    private static final String EXPECTED_END = "2024-06-01 11:00:00";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_24MultipleConditionsDateWidthMigration migration;

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("multipleconditions"))
                .where(KEY.in(LEGACY_KEY, CURRENT_KEY, PARIS_KEY, KOLKATA_KEY, NEWFOUNDLAND_KEY, NO_FRACTION_KEY))
                .execute()
        );
    }

    @Test
    void shouldAcceptBothTheLegacyAndTheWidenedFraction() throws Exception {
        // Given
        migration.migrate();

        // When — three digits is what the previous serializer wrote, six is what it writes now
        insert(LEGACY_KEY, "2024-06-01T10:00:00.000Z", "2024-06-01T11:00:00.000Z");
        insert(CURRENT_KEY, "2024-06-01T10:00:00.000000Z", "2024-06-01T11:00:00.000000Z");

        // Then
        assertThat(read(START_DATE, LEGACY_KEY)).isEqualTo(EXPECTED_START);
        assertThat(read(START_DATE, CURRENT_KEY)).isEqualTo(EXPECTED_START);
        assertThat(read(END_DATE, LEGACY_KEY)).isEqualTo(EXPECTED_END);
        assertThat(read(END_DATE, CURRENT_KEY)).isEqualTo(EXPECTED_END);
    }

    @Test
    void shouldNormaliseEveryOffsetToUtc() throws Exception {
        // Given
        migration.migrate();

        // When — the same instants written from servers in different zones
        insert(PARIS_KEY, "2024-06-01T12:00:00.000000+02:00", "2024-06-01T13:00:00.000000+02:00");
        insert(KOLKATA_KEY, "2024-06-01T15:30:00.000000+05:30", "2024-06-01T16:30:00.000000+05:30");
        insert(NEWFOUNDLAND_KEY, "2024-06-01T06:30:00.000000-03:30", "2024-06-01T07:30:00.000000-03:30");

        // Then
        for (String key : new String[]{PARIS_KEY, KOLKATA_KEY, NEWFOUNDLAND_KEY}) {
            assertThat(read(START_DATE, key)).as("start_date of %s", key).isEqualTo(EXPECTED_START);
            assertThat(read(END_DATE, key)).as("end_date of %s", key).isEqualTo(EXPECTED_END);
        }
    }

    @Test
    void shouldRejectAValueWithNoFractionRatherThanCoerceIt() throws Exception {
        // Given
        migration.migrate();

        // When / Then — the serializer never omits the fraction, so such a value did not come from
        // Kestra and must fail loudly rather than be silently coerced
        assertThatThrownBy(() -> insert(NO_FRACTION_KEY, "2024-06-01T10:00:00Z", "2024-06-01T11:00:00Z"))
            .isInstanceOf(Exception.class);
    }

    @Test
    void shouldStayCorrectWhenMigrationIsReRun() throws Exception {
        // Given / When — ALTER COLUMN must tolerate being applied to an already-migrated column
        assertThatCode(() -> {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();

        // Then
        insert(CURRENT_KEY, "2024-06-01T10:00:00.000000Z", "2024-06-01T11:00:00.000000Z");
        assertThat(read(START_DATE, CURRENT_KEY)).isEqualTo(EXPECTED_START);
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
