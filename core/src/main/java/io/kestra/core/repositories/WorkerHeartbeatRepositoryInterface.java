package io.kestra.core.repositories;

import io.kestra.core.runners.WorkerHeartbeat;

import java.util.List;
import java.util.Optional;

public interface WorkerHeartbeatRepositoryInterface {
    Optional<WorkerHeartbeat> findByWorkerUuid(String workerUuid);

    List<WorkerHeartbeat> findAll();

    void delete(WorkerHeartbeat workerHeartbeat);

    WorkerHeartbeat save(WorkerHeartbeat workerHeartbeat);
}
