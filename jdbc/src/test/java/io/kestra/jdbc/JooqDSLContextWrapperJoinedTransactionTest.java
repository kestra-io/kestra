package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JooqDSLContextWrapperJoinedTransactionTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldNotRetryDeadlockWhenJoiningABoundTransaction() {
        // Given
        ConnectionOperations<Connection> connectionOperations = mock(ConnectionOperations.class);
        when(connectionOperations.findConnectionStatus()).thenReturn(Optional.of(mock(ConnectionStatus.class)));
        JooqDSLContextWrapper wrapper = h2Wrapper(connectionOperations);
        AtomicInteger attempts = new AtomicInteger();

        // When / Then the aborted outer transaction cannot recover from an inner retry, so the owner must see the failure
        assertThatThrownBy(() -> wrapper.transactionResult(configuration ->
        {
            attempts.incrementAndGet();
            throw deadlock();
        })).isInstanceOf(DataAccessException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldRetryDeadlockWhenOwningTheTransaction() {
        // Given
        JooqDSLContextWrapper wrapper = h2Wrapper(null);
        AtomicInteger attempts = new AtomicInteger();

        // When
        String result = wrapper.transactionResult(configuration ->
        {
            if (attempts.incrementAndGet() == 1) {
                throw deadlock();
            }
            return "done";
        });

        // Then
        assertThat(result).isEqualTo("done");
        assertThat(attempts).hasValue(2);
    }

    private static DataAccessException deadlock() {
        return new DataAccessException("deadlock detected", new SQLException("deadlock detected", "40P01"));
    }

    private static JooqDSLContextWrapper h2Wrapper(ConnectionOperations<Connection> connectionOperations) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wrapper-joined-transaction-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        return new JooqDSLContextWrapper(DSL.using(dataSource, SQLDialect.H2), dataSource, connectionOperations);
    }
}
