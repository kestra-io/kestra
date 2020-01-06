package org.kestra.cli.commands.servers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.runners.AbstractExecutor;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;

@CommandLine.Command(
    name = "executor",
    description = "start an executor"
)
@Slf4j
public class ExecutorCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public ExecutorCommand() {
        super(true);
    }

    @Override
    public void run() {
        super.run();

        AbstractExecutor abstractExecutor = applicationContext.getBean(AbstractExecutor.class);
        abstractExecutor.run();

        log.info("Executor started");

        Await.until(() -> !this.applicationContext.isRunning());
    }
}