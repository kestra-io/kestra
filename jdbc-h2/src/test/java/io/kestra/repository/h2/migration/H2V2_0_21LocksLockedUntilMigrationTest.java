package io.kestra.repository.h2.migration;

import java.time.Instant;

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

/**
 * H2-specific integration test for {@link V2_0_21LocksLockedUntilMigration}: the generated
 * {@code locked_until} column must reflect the JSON value's {@code lockedUntil} for lease rows, stay
 * NULL for rows without one, and the migration must be re-runnable.
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class H2V2_0_21LocksLockedUntilMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));
    private static final Field<Instant> LOCKED_UNTIL = DSL.field(DSL.quotedName("locked_until"), Instant.class);

    private static final String LEASE_KEY = "lease_locked-until-seed-1";
    private static final String MUTEX_KEY = "mutex_locked-until-seed-1";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_21LocksLockedUntilMigration migration;

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("locks"))
                .where(KEY.in(LEASE_KEY, MUTEX_KEY))
                .execute()
        );
    }

    @Test
    void generatedColumnReflectsLockedUntilFromValue() throws Exception {
        migration.migrate();

        // KestraDateTimeModule always serializes a 6-digit fraction (production-shaped); the generated column
        // truncates to millisecond precision (LEFT(...,23)), so the round trip must drop the last 3 digits
        Instant lockedUntil = Instant.parse("2026-01-01T00:00:00.123Z");
        insertLock(LEASE_KEY, "{\"category\":\"lease\",\"id\":\"locked-until-seed-1\",\"tenantId\":\"tenant-a\",\"owner\":\"o\",\"lockedUntil\":\"2026-01-01T00:00:00.123456Z\"}");

        assertThat(readLockedUntil(LEASE_KEY)).isEqualTo(lockedUntil);
    }

    @Test
    void generatedColumnIsNullWhenValueHasNoLockedUntil() throws Exception {
        migration.migrate();

        insertLock(MUTEX_KEY, "{\"category\":\"mutex\",\"id\":\"locked-until-seed-1\",\"owner\":\"o\"}");

        assertThat(readLockedUntil(MUTEX_KEY)).isNull();
    }

    @Test
    void reRunningTheMigrationIsIdempotent() throws Exception {
        assertThatCode(() ->
        {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();

        Instant lockedUntil = Instant.parse("2026-01-01T00:00:00.123Z");
        insertLock(LEASE_KEY, "{\"category\":\"lease\",\"id\":\"locked-until-seed-1\",\"tenantId\":\"tenant-a\",\"owner\":\"o\",\"lockedUntil\":\"2026-01-01T00:00:00.123456Z\"}");
        assertThat(readLockedUntil(LEASE_KEY)).isEqualTo(lockedUntil);
    }

    private void insertLock(String key, String valueJson) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("locks"))
                .set(KEY, (Object) key)
                .set(VALUE, (Object) valueJson)
                .execute()
        );
    }

    private Instant readLockedUntil(String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(LOCKED_UNTIL)
                .from(DSL.table("locks"))
                .where(KEY.eq(key))
                .fetchOne(LOCKED_UNTIL)
        );
    }
}
