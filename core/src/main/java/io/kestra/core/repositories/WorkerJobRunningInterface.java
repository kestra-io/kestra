package io.kestra.core.repositories;


import io.kestra.core.runners.WorkerJobRunning;

import java.util.List;
import java.util.Optional;

public interface WorkerJobRunningInterface {
    Optional<WorkerJobRunning> findByTaskRunId(String taskRunId);

    void delete(String taskRunId);

    List<WorkerJobRunning> getWorkerJobWithWorkerDead(List<String> workersAlive);
}
