package io.kestra.controller.messages;

import io.kestra.core.runners.WorkerJob;

import java.util.List;
import java.util.Optional;

public record WorkerJobBatchMessage(
    List<WorkerJob> jobs
) {
    public List<WorkerJob> jobs() {
        return Optional.ofNullable(jobs).orElse(List.of());
    }
}
