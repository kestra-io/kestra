package io.kestra.core.repositories;

import java.util.Optional;

import io.kestra.core.runners.WorkerJobRunning;

public interface WorkerJobRunningRepositoryInterface {
    Optional<WorkerJobRunning> findByKey(String uid);

    void deleteByKey(String uid);

}
