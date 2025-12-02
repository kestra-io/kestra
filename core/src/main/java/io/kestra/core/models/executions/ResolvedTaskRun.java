package io.kestra.core.models.executions;

import io.kestra.core.models.tasks.ResolvedTask;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResolvedTaskRun {
    TaskRun taskRun;

    ResolvedTask resolvedTask;
}
