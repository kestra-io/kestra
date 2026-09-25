package io.kestra.jdbc.repository;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;

import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTimeoutTest {
    @Test
    void shouldSetTheQueryTimeoutInWholeSeconds() {
        Configuration configuration = DSL.using(SQLDialect.H2).configuration();

        assertThat(QueryTimeout.apply(configuration, Duration.ofSeconds(30)).settings().getQueryTimeout()).isEqualTo(30);
        assertThat(QueryTimeout.apply(configuration, Duration.ofMillis(500)).settings().getQueryTimeout()).isEqualTo(1);
        assertThat(QueryTimeout.apply(configuration, null).settings().getQueryTimeout()).isZero();
        assertThat(QueryTimeout.apply(configuration, Duration.ZERO).settings().getQueryTimeout()).isZero();
    }

    @Test
    void shouldReportADriverTimeoutAsAQueryTimeout() {
        assertThatThrownBy(() -> run(new SQLTimeoutException("Statement was canceled or the session timed out", "57014")))
            .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void shouldReportAPostgresCancelledStatementAsAQueryTimeout() {
        assertThatThrownBy(() -> run(new SQLException("ERROR: canceling statement due to user request", "57014")))
            .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void shouldLeaveOtherErrorsAsTheyAre() {
        assertThatThrownBy(() -> run(new SQLException("relation \"executions\" does not exist", "42P01")))
            .isInstanceOf(DataAccessException.class)
            .isNotInstanceOf(QueryTimeoutException.class);
    }

    private static void run(SQLException failure) {
        Configuration configuration = DSL.using(new MockConnection(context -> {
            throw failure;
        }), SQLDialect.POSTGRES).configuration();

        QueryTimeout.apply(configuration, Duration.ofSeconds(1)).selectOne().fetch();
    }
}
