package io.kestra.jdbc.runner;

import io.kestra.executor.TransactionContext;
import org.jooq.DSLContext;

public class JdbcTransactionContext implements TransactionContext {

    private final DSLContext dslContext;

    public JdbcTransactionContext(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public DSLContext getDslContext() {
        return dslContext;
    }
}
