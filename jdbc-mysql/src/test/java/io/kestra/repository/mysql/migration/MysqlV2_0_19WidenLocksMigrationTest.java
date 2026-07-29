package io.kestra.repository.mysql.migration;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * MySQL-specific integration test for {@link V2_0_19WidenLocksColumnsMigration}. Like Postgres,
 * MySQL widens {@code locks.key}/{@code locks.id} with an in-place {@code MODIFY COLUMN}, so this
 * test proves that an EXISTING row survives the in-place widen, that the widened column accepts an
 * asset-sized id, and that re-running the migration is safe (idempotent).
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class MysqlV2_0_19WidenLocksMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));
    private static final Field<Object> ID = DSL.field(DSL.quotedName("id"));

    private static final String SEED_KEY = "lease_widen-seed-1";
    private static final String LONG_KEY = "lease_widen-long-1";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_19WidenLocksColumnsMigration migration;

    @BeforeEach
    @AfterEach
    void cleanup() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .deleteFrom(DSL.table("locks"))
                .where(KEY.in(SEED_KEY, LONG_KEY))
                .execute()
        );
    }

    @Test
    void existingRowSurvivesInPlaceWiden() throws Exception {
        insertLock(SEED_KEY, "{\"category\":\"lease\",\"id\":\"widen-seed-1\",\"owner\":\"o\"}");

        migration.migrate();

        assertThat(readId(SEED_KEY)).isEqualTo("widen-seed-1");
    }

    @Test
    void widenedColumnAcceptsAssetSizedId() throws Exception {
        migration.migrate();

        String longId = "a".repeat(480); // exceeds the original VARCHAR(150)
        assertThatCode(() -> insertLock(LONG_KEY, "{\"category\":\"lease\",\"id\":\"" + longId + "\",\"owner\":\"o\"}"))
            .doesNotThrowAnyException();
        assertThat(readId(LONG_KEY)).isEqualTo(longId);
    }

    @Test
    void reRunningTheMigrationIsIdempotent() throws Exception {
        insertLock(SEED_KEY, "{\"category\":\"lease\",\"id\":\"widen-seed-1\",\"owner\":\"o\"}");

        assertThatCode(() ->
        {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();

        assertThat(readId(SEED_KEY)).isEqualTo("widen-seed-1");
    }

    private void insertLock(String key, String valueJson) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("locks"))
                .set(KEY, (Object) key)
                .set(VALUE, (Object) JdbcJsonbUtils.valueOf(valueJson))
                .execute()
        );
    }

    private String readId(String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(ID)
                .from(DSL.table("locks"))
                .where(KEY.eq(key))
                .fetchOne(ID, String.class)
        );
    }
}
