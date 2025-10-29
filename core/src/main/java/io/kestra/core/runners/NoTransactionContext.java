package io.kestra.core.runners;

public final class NoTransactionContext implements TransactionContext {
    public static final NoTransactionContext INSTANCE = new NoTransactionContext();

    private NoTransactionContext() {
        // should only have one instance
    }
}
