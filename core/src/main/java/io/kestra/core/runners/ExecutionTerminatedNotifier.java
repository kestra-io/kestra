package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

public interface ExecutionTerminatedNotifier {

    void executionTerminated(Execution execution);

    @Singleton
    @Secondary
    class NoopExecutionTerminatedNotifier implements ExecutionTerminatedNotifier {
        @Override
        public void executionTerminated(Execution execution) {
            // no-op
        }
    }
}
