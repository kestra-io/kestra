package io.kestra.core.repositories;


import io.kestra.core.runners.WorkerJobRunning;

import java.util.Optional;

public interface WorkerJobRunningRepositoryInterface {
    Optional<WorkerJobRunning> findByKey(String uid);

    void deleteByKey(String uid);

}
