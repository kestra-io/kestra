package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.Transaction;
import org.jooq.TransactionContext;
import org.jooq.TransactionProvider;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JooqDSLContextWrapperConnectionFailureTest {
    private static final String POOL_ERROR = "HikariPool-1 - Connection is not available, request timed out after 30000ms";

    @Test
    void shouldSurfaceOnlyThePoolErrorWhenTransactionCannotObtainAConnection() throws SQLException {
        // Given
        JooqDSLContextWrapper wrapper = wrapperFailingToObtainConnection();

        // When / Then the driver error surfaces in place of Micronaut's generic message, and the NPE jOOQ
        // triggers by rolling back the transaction that was never opened is gone
        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
        }))
            .isInstanceOf(DataAccessException.class)
            .hasMessage("Unable to obtain a database connection: %s".formatted(POOL_ERROR))
            .hasCauseInstanceOf(SQLException.class)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());
    }

    @Test
    void shouldLeaveTransactionFailuresUntouched() {
        // Given
        JooqDSLContextWrapper wrapper = h2Wrapper();
        IllegalStateException failure = new IllegalStateException("task logic failed");

        // When / Then a failure raised by the transactional work is not mistaken for a connection failure
        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
            throw failure;
        })).isSameAs(failure);
    }

    private static JooqDSLContextWrapper wrapperFailingToObtainConnection() throws SQLException {
        SQLException poolError = new SQLException(POOL_ERROR);

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(poolError);
        when(dataSource.getConnection(any(), any())).thenThrow(poolError);

        Configuration configuration = DSL.using(dataSource, SQLDialect.H2)
            .configuration()
            .derive(new FailedBeginTransactionProvider(dataSource));

        return new JooqDSLContextWrapper(DSL.using(configuration), dataSource);
    }

    private static JooqDSLContextWrapper h2Wrapper() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wrapper-connection-failure-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        return new JooqDSLContextWrapper(DSL.using(dataSource, SQLDialect.H2), dataSource);
    }

    /**
     * Mirrors {@code io.micronaut.configuration.jooq.MicronautTransactionProvider}: a connection failure
     * escapes {@code begin} before the {@link TransactionContext} holds a transaction, and {@code rollback}
     * then dereferences that missing one instead of doing nothing.
     */
    private record FailedBeginTransactionProvider(DataSource dataSource) implements TransactionProvider {
        @Override
        public void begin(TransactionContext ctx) {
            try {
                dataSource.getConnection();
            } catch (SQLException e) {
                throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", e);
            }
        }

        @Override
        public void commit(TransactionContext ctx) {
        }

        @Override
        public void rollback(TransactionContext ctx) {
            ((ConnectionTransaction) ctx.transaction()).connection();
        }
    }

    /** Stands in for {@code MicronautTransaction}, whose {@code getTxStatus()} the provider calls on rollback. */
    private interface ConnectionTransaction extends Transaction {
        Connection connection();
    }
}
