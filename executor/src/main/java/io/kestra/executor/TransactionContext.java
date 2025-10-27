package io.kestra.executor;

public interface TransactionContext {
    default <T extends TransactionContext> T unwrap(Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException("Cannot unwrap " + this.getClass().getName() + " to " + clazz.getName());
    }
}
