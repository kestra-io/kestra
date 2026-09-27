package io.kestra.core.server;

/**
 * Hook invoked when a controller submits a batch async operation (execution/trigger/backfill
 * kill, pause, delete, ...), before the operation's items are emitted onto their domain queue.
 * <p>
 * Implementations are registered as Micronaut beans and discovered automatically by the
 * submitting controller/service. They must be fast and non-throwing: a broken listener must not
 * block or fail the operation it is being notified about.
 * <p>
 * Invocation order across registered listeners is not guaranteed.
 */
public interface AsyncOperationListener {

    /**
     * @param operationId the identifier shared by every item this operation emits
     * @param operationType the kind of operation submitted
     * @param itemCount the number of items the operation was submitted for
     */
    void onAsyncOperationCreated(String operationId, AsyncOperationType operationType, int itemCount);

}
