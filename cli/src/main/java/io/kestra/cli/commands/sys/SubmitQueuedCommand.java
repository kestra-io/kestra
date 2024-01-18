package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Optional;

@CommandLine.Command(
    name = "submit-queued-execution",
    description = {"Submit all queued execution to the executor",
        "All queued execution will be submitted to the executor. Warning, if there is still running executions and concurrency limit configured, the executions may be queued again."
    }
)
@Slf4j
public class SubmitQueuedCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

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
        }
        else if (queueType.get().equals("postgres") || queueType.get().equals("mysql") || queueType.get().equals("h2")) {
            var executionQueuedStorage = applicationContext.getBean(AbstractJdbcExecutionQueuedStorage.class);

            for (ExecutionQueued queued : executionQueuedStorage.getAllForAllTenants()) {
                executionQueuedStorage.pop(queued.getTenantId(), queued.getNamespace(), queued.getFlowId(), execution -> executionQueue.emit(execution.withState(State.Type.CREATED)));
                cpt++;
            }
        }
        else {
            stdOut("Unable to submit queued executions, the 'kestra.queue.type' is set to an unknown type '{0}'", queueType.get());
            return 1;
        }

        stdOut("Successfully submitted {0} queued executions", cpt);
        return 0;
    }
}
