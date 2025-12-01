package io.kestra.core.models.executions;

import io.kestra.core.models.tasks.ResolvedTask;
import lombok.Builder;
import lombok.Value;

/**
 * Holds both the runtime state of a task (TaskRun) and
 * its resolved definition (ResolvedTask).
 *
 * Used to avoid repeated task resolution during execution processing.
 */
@Value
@Builder
public class ResolvedTaskRun {
    
    // Runtime information for this task execution. 
    TaskRun taskRun;

    //Static definition resolved from the Flow.
    ResolvedTask resolvedTask;
}
