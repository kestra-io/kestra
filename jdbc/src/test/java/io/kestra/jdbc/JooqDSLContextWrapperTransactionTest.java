package io.kestra.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JooqDSLContextWrapperTransactionTest {
    private static final Table<Record> TABLE = DSL.table("wrapper_tx");
    private static final Field<Integer> ID = DSL.field("id", Integer.class);
    private static final Field<String> VALUE = DSL.field("val", String.class);

    private DSLContext dsl;
    private JooqDSLContextWrapper wrapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wrapper-tx-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        dsl = DSL.using(dataSource, SQLDialect.H2);
        dsl.execute("CREATE TABLE wrapper_tx (id INT PRIMARY KEY, val VARCHAR(255))");
        wrapper = new JooqDSLContextWrapper(dsl, dataSource);
    }

    @Test
    void shouldCommitTransactionWhenWorkSucceeds() {
        wrapper.transaction(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
        });

        assertThat(count()).isOne();
    }

    @Test
    void shouldRollbackTransactionWhenWorkFails() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
            throw failure;
        })).isSameAs(failure);

        assertThat(count()).isZero();
    }

    @Test
    void shouldJoinOngoingTransactionWhenNestedTransactionCalled() {
        Integer inserted = wrapper.transactionResult(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
            return wrapper.transactionResult(
                nested -> DSL.using(nested).insertInto(TABLE).set(ID, 2).set(VALUE, "two").execute()
            );
        });

        assertThat(inserted).isOne();
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void shouldRollbackNestedWorkWhenOuterTransactionFails() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
            wrapper.transaction(
                nested -> DSL.using(nested).insertInto(TABLE).set(ID, 2).set(VALUE, "two").execute()
            );
            throw failure;
        })).isSameAs(failure);

        assertThat(count()).isZero();
    }

    @Test
    void shouldCommitRequireNewTransactionWhenOuterTransactionRollsBack() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
            wrapper.requireNewTransaction(
                requiresNew -> DSL.using(requiresNew).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute()
            );
            DSL.using(configuration).insertInto(TABLE).set(ID, 2).set(VALUE, "two").execute();
            throw failure;
        })).isSameAs(failure);

        assertThat(values()).containsExactly("one");
    }

    @Test
    void shouldSurfaceConnectionFailureWithoutTransactionError() {
        DataSource failing = new FailingDataSource(new SQLException("connection refused"));
        JooqDSLContextWrapper failingWrapper = new JooqDSLContextWrapper(DSL.using(failing, SQLDialect.H2), failing);

        assertThatThrownBy(() -> failingWrapper.transaction(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
        }))
            .isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("connection refused");

        assertThatThrownBy(() -> failingWrapper.transaction(configuration ->
        {
            DSL.using(configuration).insertInto(TABLE).set(ID, 1).set(VALUE, "one").execute();
        }))
            .satisfies(thrown -> assertThat(allThrowables(thrown)).noneMatch(JooqDSLContextWrapperTransactionTest::isTransactionNpe));
    }

    private int count() {
        return dsl.selectCount().from(TABLE).fetchOne(0, Integer.class);
    }

    private List<String> values() {
        return dsl.select(VALUE).from(TABLE).orderBy(ID).fetch(VALUE);
    }

    private static List<Throwable> allThrowables(Throwable root) {
        List<Throwable> all = new ArrayList<>();
        Set<Throwable> seen = new HashSet<>();
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Throwable current = pending.poll();
            if (!seen.add(current)) {
                continue;
            }
            all.add(current);
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.add(suppressed);
            }
        }
        return all;
    }

    private static boolean isTransactionNpe(Throwable throwable) {
        return throwable instanceof NullPointerException
            && throwable.getMessage() != null
            && throwable.getMessage().contains("getTxStatus");
    }

    private record FailingDataSource(SQLException failure) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw failure;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw failure;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
