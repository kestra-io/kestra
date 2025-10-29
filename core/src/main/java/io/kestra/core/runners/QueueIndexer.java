package io.kestra.core.runners;

public interface QueueIndexer {
    void accept(TransactionContext txContext, Object item);
}
