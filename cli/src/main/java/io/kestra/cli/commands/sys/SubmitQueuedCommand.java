package io.kestra.cli.commands.sys;

import java.util.Optional;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Unqueue;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStateStore;

import io.micronaut.context.ApplicationContext;
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
    private ApplicationContext applicationContext;

    @Inject
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Override
    public Integer call() throws Exception {
        super.call();

        Optional<String> queueType = applicationContext.getProperty("kestra.queue.type", String.class);
        if (queueType.isEmpty()) {
            stdOut("Unable to submit queued executions, the 'kestra.queue.type' configuration is not set");
            return 0;
        }

        int cpt = 0;
        if (queueType.get().equals("kafka")) {
            stdOut("Unable to submit queued executions, the 'kestra.queue.type' configuration is set to 'kafka', use the corresponding sys-ee command");
            return 1;
        } else if (queueType.get().equals("postgres") || queueType.get().equals("mysql") || queueType.get().equals("h2")) {
            var executionQueuedStorage = applicationContext.getBean(AbstractJdbcExecutionQueuedStateStore.class);

            for (ExecutionQueued queued : executionQueuedStorage.getAllForAllTenants()) {
                var executionCommand = Unqueue.from(queued.getExecution(), State.Type.RUNNING);
                executionCommandQueue.emit(executionCommand);
                cpt++;
            }
        } else {
            stdOut("Unable to submit queued executions, the 'kestra.queue.type' is set to an unknown type '{0}'", queueType.get());
            return 1;
        }

        stdOut("Successfully submitted {0} queued executions", cpt);
        return 0;
    }
}
