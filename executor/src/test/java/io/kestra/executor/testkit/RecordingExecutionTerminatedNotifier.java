package io.kestra.executor.testkit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionTerminatedNotifier;

/**
 * Records the executions the executor reported as terminated to the notifier hook.
 */
public class RecordingExecutionTerminatedNotifier implements ExecutionTerminatedNotifier {
    public static final String NAME = "executionTerminated";

    private final List<Execution> terminated = new CopyOnWriteArrayList<>();
    private final EmissionJournal journal;

    public RecordingExecutionTerminatedNotifier(EmissionJournal journal) {
        this.journal = journal;
    }

    @Override
    public void executionTerminated(Execution execution) {
        terminated.add(execution);
        journal.record(NAME, execution);
    }

    public List<Execution> terminated() {
        return List.copyOf(terminated);
    }
}
