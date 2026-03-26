package io.kestra.core.runners;

public final class NoTransactionContext implements TransactionContext {
    public static final NoTransactionContext INSTANCE = new NoTransactionContext();

    private NoTransactionContext() {
        // should only have one instance
    }

    @Override
    public <T extends TransactionContext> boolean supports(Class<T> clazz) {
        return NoTransactionContext.class.isAssignableFrom(clazz);
    }
}
