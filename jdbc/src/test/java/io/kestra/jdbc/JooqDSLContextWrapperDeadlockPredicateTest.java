package io.kestra.jdbc;

import java.sql.SQLException;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JooqDSLContextWrapperDeadlockPredicateTest {
    private final JooqDSLContextWrapper.DeadlockPredicate predicate = new JooqDSLContextWrapper.DeadlockPredicate();

    @Test
    void shouldMatchDirectPostgresDeadlock() {
        // Given
        SQLException postgresDeadlock = new SQLException("ERROR: deadlock detected", "40P01");
        DataAccessException exception = new DataAccessException("deadlock", postgresDeadlock);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldMatchDirectMysqlDeadlock() {
        // Given
        // vendor code 1213 = ER_LOCK_DEADLOCK
        SQLException mysqlDeadlock = new SQLException("Deadlock found when trying to get lock", "40001", 1213);
        DataAccessException exception = new DataAccessException("deadlock", mysqlDeadlock);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldMatchMysqlLockWaitTimeout() {
        // Given
        // vendor code 1205 = ER_LOCK_WAIT_TIMEOUT
        SQLException lockWaitTimeout = new SQLException("Lock wait timeout exceeded", "HY000", 1205);
        DataAccessException exception = new DataAccessException("lock wait timeout", lockWaitTimeout);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldMatchStandardSqlStateDeadlock() {
        // Given
        SQLException standardDeadlock = new SQLException("deadlock detected", "40001");
        DataAccessException exception = new DataAccessException("deadlock", standardDeadlock);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldMatchDeadlockNestedTwoLevelsDeep() {
        // Given
        // Reproduces the CI failure shape: once Postgres aborts a transaction after a deadlock, a
        // later statement in the same failed attempt surfaces a secondary "current transaction is
        // aborted" (25P02) exception wrapping the original deadlock (40P01) one level deeper.
        SQLException originalDeadlock = new SQLException("ERROR: deadlock detected", "40P01");
        SQLException abortedTransaction = new SQLException("ERROR: current transaction is aborted, commands ignored until end of transaction block", "25P02", originalDeadlock);
        DataAccessException exception = new DataAccessException("aborted", abortedTransaction);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotMatchUnrelatedSqlException() {
        // Given
        SQLException unrelated = new SQLException("syntax error", "42601");
        DataAccessException exception = new DataAccessException("syntax error", unrelated);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotMatchExceptionWithNoCause() {
        // Given
        DataAccessException exception = new DataAccessException("no cause here");

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldTerminateOnCyclicCauseChainInsteadOfMatching() {
        // Given
        // Some libraries produce cause chains that cycle back to an already-seen exception. Neither
        // exception here is a deadlock, so the predicate must terminate and return false rather than
        // looping forever walking the cycle.
        SQLException a = new SQLException("a");
        SQLException b = new SQLException("b");
        a.initCause(b);
        b.initCause(a);
        DataAccessException exception = new DataAccessException("cyclic", a);

        // When
        boolean result = predicate.test(exception);

        // Then
        assertThat(result).isFalse();
    }
}
