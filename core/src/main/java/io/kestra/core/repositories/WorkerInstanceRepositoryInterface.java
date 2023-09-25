package io.kestra.core.repositories;

import io.kestra.core.runners.WorkerInstance;

import java.util.List;
import java.util.Optional;

public interface WorkerInstanceRepositoryInterface {
    Optional<WorkerInstance> findByWorkerUuid(String workerUuid);

    List<WorkerInstance> findAll();

    void delete(WorkerInstance workerInstance);

    WorkerInstance save(WorkerInstance workerInstance);
}
