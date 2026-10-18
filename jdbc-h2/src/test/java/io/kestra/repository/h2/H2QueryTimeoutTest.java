package io.kestra.repository.h2;

import java.time.Duration;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.QueryTimeoutException;
import io.kestra.jdbc.repository.QueryTimeout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H2QueryTimeoutTest {
    @Test
    void shouldCancelAQueryThatRunsPastTheTimeout() {
        DSLContext context = DSL.using("jdbc:h2:mem:query-timeout;DB_CLOSE_DELAY=-1");
        context.execute("CREATE ALIAS IF NOT EXISTS SLEEP FOR 'java.lang.Thread.sleep(long)'");

        assertThatThrownBy(() -> context.transactionResult(configuration ->
            QueryTimeout.apply(configuration, Duration.ofSeconds(1)).fetch("SELECT SLEEP(20) FROM SYSTEM_RANGE(1, 1000)")
        )).isInstanceOf(QueryTimeoutException.class);
    }
}
