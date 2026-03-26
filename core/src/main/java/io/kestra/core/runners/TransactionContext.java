package io.kestra.core.runners;

public interface TransactionContext {
    default <T extends TransactionContext> T unwrap(Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException("Cannot unwrap " + this.getClass().getName() + " to " + clazz.getName());
    }

    <T extends TransactionContext> boolean supports(Class<T> clazz);
}
