package io.kestra.jdbc;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.TransactionProvider;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JooqTransactionProviderTest {
    @Test
    void shouldPreserveConnectionFailureWhenTransactionCannotBegin() {
        var transactionManager = mock(DataSourceTransactionManager.class);
        var connectionFailure = new SQLException("Database is unavailable.");
        var exception = new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", connectionFailure);
        when(transactionManager.getTransaction(TransactionDefinition.DEFAULT)).thenThrow(exception);

        try (var context = ApplicationContext.builder().deduceEnvironment(false).start()) {
            context.registerSingleton(DataSourceTransactionManager.class, transactionManager, Qualifiers.byName("test"));
            var provider = context.getBean(TransactionProvider.class, Qualifiers.byName("test"));
            var dslContext = DSL.using(new DefaultConfiguration().set(provider));
            var wrapper = new JooqDSLContextWrapper(dslContext, mock(DataSource.class));

            var failure = catchThrowable(() -> wrapper.transaction(configuration -> {
                throw new AssertionError("Transaction work must not run when connection acquisition fails.");
            }));

            assertThat(failure).isSameAs(exception).hasCause(connectionFailure);
            assertThat(failure.getSuppressed()).isEmpty();
        }
    }

    @Test
    void shouldCommitSuccessfulWorkAndRollBackFailedWork() {
        try (var context = ApplicationContext.builder().deduceEnvironment(false).properties(Map.of(
            "datasources.test.url", "jdbc:h2:mem:transaction-provider",
            "datasources.test.driverClassName", "org.h2.Driver",
            "datasources.test.username", "sa"
        )).start()) {
            var dslContext = context.getBean(DSLContext.class, Qualifiers.byName("test"));
            var wrapper = new JooqDSLContextWrapper(dslContext, context.getBean(DataSource.class, Qualifiers.byName("test")));
            wrapper.transaction(configuration -> DSL.using(configuration).execute("CREATE TABLE test_transactions (id INT)"));

            var result = wrapper.transactionResult(configuration -> {
                DSL.using(configuration).execute("INSERT INTO test_transactions VALUES (1)");
                return "committed";
            });

            assertThat(result).isEqualTo("committed");
            var committedRows = wrapper.transactionResult(configuration -> DSL.using(configuration).fetchCount(DSL.table("test_transactions")));
            assertThat(committedRows).isEqualTo(1);

            var exception = new IllegalStateException("Transaction work failed.");
            var failure = catchThrowable(() -> wrapper.transaction(configuration -> {
                DSL.using(configuration).execute("INSERT INTO test_transactions VALUES (2)");
                throw exception;
            }));

            assertThat(failure).isSameAs(exception);
            assertThat(failure.getSuppressed()).isEmpty();
            var remainingRows = wrapper.transactionResult(configuration -> DSL.using(configuration).fetchCount(DSL.table("test_transactions")));
            assertThat(remainingRows).isEqualTo(1);
        }
    }
}
