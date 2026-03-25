package io.kestra.controller.messages;

import java.util.List;
import java.util.Optional;

import io.kestra.core.runners.WorkerJob;

public record WorkerJobBatchMessage(
    List<WorkerJob> jobs) {
    public List<WorkerJob> jobs() {
        return Optional.ofNullable(jobs).orElse(List.of());
    }
}
