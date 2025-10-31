package io.kestra.core.runners;

public interface QueueIndexerRepository<T>  {
    T save(TransactionContext txContext, T message);

    T save(T item);

    Class<T> getItemClass();

    <TX extends TransactionContext> boolean supports(Class<TX> clazz);
}
