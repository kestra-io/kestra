package io.kestra.core.killswitch;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.IgnoreExecutionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KillSwitchService {
    private final IgnoreExecutionService ignoreExecutionService;

    @Inject
    public KillSwitchService(IgnoreExecutionService ignoreExecutionService) {
        this.ignoreExecutionService = ignoreExecutionService;
    }

    /**
     * Warning: this method didn't check the flow, so it must be used only when neither of the others can be used.
     */
    public EvaluationType evaluate(String executionId) {
        if (ignoreExecutionService.ignoreExecution(executionId)) {
            return EvaluationType.IGNORE;
        }
        return EvaluationType.PASS;
    }

    public EvaluationType evaluate(Execution execution) {
        if (ignoreExecutionService.ignoreExecution(execution)) {
            return EvaluationType.IGNORE;
        }
        return EvaluationType.PASS;
    }

    public EvaluationType evaluate(TaskRun taskRun) {
        if (ignoreExecutionService.ignoreExecution(taskRun)) {
            return EvaluationType.IGNORE;
        }
        return EvaluationType.PASS;
    }
}
