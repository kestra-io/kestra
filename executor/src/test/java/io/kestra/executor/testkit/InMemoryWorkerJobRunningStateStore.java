package io.kestra.executor.testkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerJobRunning;

/**
 * Map-backed {@link WorkerJobRunningStateStore} that also records the keys passed to
 * {@code deleteByKey}, so a test can assert which running entries the executor released.
 */
public class InMemoryWorkerJobRunningStateStore implements WorkerJobRunningStateStore {
    private final Map<String, WorkerJobRunning> entries = new ConcurrentHashMap<>();
    private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

    @Override
    public void deleteByKey(String key) {
        deletedKeys.add(key);
        entries.remove(key);
    }

    @Override
    public void deleteByKey(TransactionContext txContext, String key) {
        deleteByKey(key);
    }

    @Override
    public WorkerJobRunning save(TransactionContext txContext, WorkerJobRunning workerJobRunning) {
        entries.put(workerJobRunning.uid(), workerJobRunning);
        return workerJobRunning;
    }

    @Override
    public void processWorkerJobsForDeadWorker(TransactionContext txContext, String workersUid, BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        entries.values().stream()
            .filter(entry -> workersUid.equals(entry.getWorkerInstance().uid()))
            .forEach(entry -> consumer.accept(txContext, entry));
    }

    public List<String> deletedKeys() {
        return List.copyOf(deletedKeys);
    }
}
