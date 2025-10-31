package io.kestra.jdbc.runner;

import io.kestra.core.runners.TransactionContext;
import org.jooq.DSLContext;

public class JdbcTransactionContext implements TransactionContext {

    private final DSLContext dslContext;

    public JdbcTransactionContext(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public DSLContext getDslContext() {
        return dslContext;
    }

    @Override
    public <T extends TransactionContext> boolean supports(Class<T> clazz) {
        return JdbcTransactionContext.class.isAssignableFrom(clazz);
    }
}
