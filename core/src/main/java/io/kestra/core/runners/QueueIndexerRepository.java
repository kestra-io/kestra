package io.kestra.core.runners;

public interface QueueIndexerRepository<T>  {
    T save(TransactionContext txContext, T message);

    Class<T> getItemClass();
}
