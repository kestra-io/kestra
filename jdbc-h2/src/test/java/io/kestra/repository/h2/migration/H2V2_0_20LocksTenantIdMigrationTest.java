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

/**
 * H2-specific integration test for {@link V2_0_20LocksTenantIdMigration}: the generated
 * {@code tenant_id} column must reflect the JSON value's {@code tenantId} for lease rows, stay
 * NULL for rows without one (e.g. server-mutex Locks), and the migration must be re-runnable.
 */
@MicronautTest(transactional = false)
@Execution(ExecutionMode.SAME_THREAD)
class H2V2_0_20LocksTenantIdMigrationTest {

    private static final Field<Object> KEY = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE = DSL.field(DSL.quotedName("value"));
    private static final Field<Object> TENANT_ID = DSL.field(DSL.quotedName("tenant_id"));

    private static final String LEASE_KEY = "lease_tenant-id-seed-1";
    private static final String MUTEX_KEY = "mutex_tenant-id-seed-1";

    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    V2_0_20LocksTenantIdMigration migration;

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
    void generatedColumnReflectsTenantIdFromValue() throws Exception {
        migration.migrate();

        insertLock(LEASE_KEY, "{\"category\":\"lease\",\"id\":\"tenant-id-seed-1\",\"tenantId\":\"tenant-a\",\"owner\":\"o\"}");

        assertThat(readTenantId(LEASE_KEY)).isEqualTo("tenant-a");
    }

    @Test
    void generatedColumnIsNullWhenValueHasNoTenantId() throws Exception {
        migration.migrate();

        insertLock(MUTEX_KEY, "{\"category\":\"mutex\",\"id\":\"tenant-id-seed-1\",\"owner\":\"o\"}");

        assertThat(readTenantId(MUTEX_KEY)).isNull();
    }

    @Test
    void reRunningTheMigrationIsIdempotent() throws Exception {
        assertThatCode(() ->
        {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();

        insertLock(LEASE_KEY, "{\"category\":\"lease\",\"id\":\"tenant-id-seed-1\",\"tenantId\":\"tenant-a\",\"owner\":\"o\"}");
        assertThat(readTenantId(LEASE_KEY)).isEqualTo("tenant-a");
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

    private String readTenantId(String key) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(TENANT_ID)
                .from(DSL.table("locks"))
                .where(KEY.eq(key))
                .fetchOne(TENANT_ID, String.class)
        );
    }
}
