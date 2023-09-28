package io.kestra.core.repositories;


import io.kestra.core.runners.WorkerJobRunning;

import java.util.Optional;

public interface WorkerJobRunningRepositoryInterface {
    Optional<WorkerJobRunning> findByTaskRunId(String taskRunId);

    void deleteByTaskRunId(String taskRunId);

}
