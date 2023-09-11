package io.kestra.core.repositories;

import io.kestra.core.runners.WorkerInstance;

import java.util.List;

public interface WorkerInstanceRepositoryInterface {
    List<WorkerInstance> findAll();

    WorkerInstance save(WorkerInstance workerInstance);
}
