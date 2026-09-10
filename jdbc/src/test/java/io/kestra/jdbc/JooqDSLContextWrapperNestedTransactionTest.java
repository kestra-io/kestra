package io.kestra.jdbc;

import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * kestra-ee#10347: a write that violates a NOT NULL constraint on a caller-owned (e.g.
 * dispatch-queue poll) transaction must not abort that transaction. On Postgres, a plain
 * {@link JooqDSLContextWrapper#transaction} joins the caller's transaction, so catching the
 * exception in Java can't undo the DB-level abort — the next statement on that same
 * transaction fails too. {@link JooqDSLContextWrapper#nestedTransactionResult} exists to isolate
 * exactly this: a SAVEPOINT-scoped nested transaction that rolls back to the savepoint on
 * failure, leaving the caller's transaction untouched.
 */
@KestraTest
public abstract class JooqDSLContextWrapperNestedTransactionTest {

    @Inject
    private JooqDSLContextWrapper dslContextWrapper;

    @BeforeEach
    void setUp() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).execute("CREATE TABLE IF NOT EXISTS nested_tx_test (id VARCHAR(50) NOT NULL PRIMARY KEY)")
        );
    }

    @AfterEach
    void tearDown() {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration).execute("DELETE FROM nested_tx_test")
        );
    }

    @Test
    void nestedTransactionRollsBackWithoutAbortingCallerTransaction() {
        String survivorId = IdUtils.create();

        dslContextWrapper.transaction(configuration ->
        {
            assertThatThrownBy(
                () -> dslContextWrapper.nestedTransactionResult(
                    nestedConfiguration -> DSL.using(nestedConfiguration)
                        .insertInto(DSL.table("nested_tx_test"), DSL.field("id"))
                        .values((Object) null)
                        .execute()
                )
            ).isInstanceOf(DataAccessException.class);

            // still inside the SAME caller transaction: this write must succeed, proving the
            // constraint violation above did not abort it (on Postgres, a doomed transaction
            // rejects every further statement with "current transaction is aborted")
            DSL.using(configuration)
                .insertInto(DSL.table("nested_tx_test"), DSL.field("id"))
                .values(survivorId)
                .execute();
        });

        Integer count = dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .selectCount()
                .from(DSL.table("nested_tx_test"))
                .where(DSL.field("id").eq(survivorId))
                .fetchOne(0, Integer.class)
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nestedTransactionWithNoAmbientTransactionBehavesLikeANewOne() {
        String id = IdUtils.create();

        Integer inserted = dslContextWrapper.nestedTransactionResult(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("nested_tx_test"), DSL.field("id"))
                .values(id)
                .execute()
        );
        assertThat(inserted).isEqualTo(1);

        Integer count = dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .selectCount()
                .from(DSL.table("nested_tx_test"))
                .where(DSL.field("id").eq(id))
                .fetchOne(0, Integer.class)
        );
        assertThat(count).isEqualTo(1);
    }
}
