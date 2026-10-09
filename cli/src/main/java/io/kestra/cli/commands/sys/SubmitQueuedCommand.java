package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Unqueue;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "submit-queued-execution",
    description = { "Submit all queued execution to the executor",
        "All queued execution will be submitted to the executor. Warning, if there is still running executions and concurrency limit configured, the executions may be queued again."
    }
)
@Slf4j
public class SubmitQueuedCommand extends AbstractCommand {
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;

    @Inject
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Override
    public Integer call() throws Exception {
        super.call();

        int cpt = 0;
        for (ExecutionQueued queued : executionQueuedStateStore.getAllForAllTenants()) {
            var executionCommand = Unqueue.from(queued.getExecution(), State.Type.RUNNING);
            executionCommandQueue.emit(executionCommand);
            cpt++;
        }

        stdOut("Successfully submitted {0} queued executions", cpt);
        return 0;
    }
}
