package io.kestra.executor.testkit;

import io.kestra.core.runners.TransactionContext;

/**
 * {@link TransactionContext} for in-memory fakes: there is no transaction to attach to.
 */
public class NoopTransactionContext implements TransactionContext {
    public static final NoopTransactionContext INSTANCE = new NoopTransactionContext();

    @Override
    public <T extends TransactionContext> boolean supports(Class<T> clazz) {
        return clazz.isInstance(this);
    }
}
