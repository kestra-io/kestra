package io.kestra.controller.messages;

import java.util.List;

import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.ListUtils;

public record WorkerJobBatchMessage(
    List<WorkerJob> jobs) {
    public List<WorkerJob> jobs() {
        return ListUtils.emptyOnNull(jobs);
    }
}
