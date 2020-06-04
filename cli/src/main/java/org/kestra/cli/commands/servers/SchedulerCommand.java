package org.kestra.cli.commands.servers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.schedulers.Scheduler;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;

@CommandLine.Command(
    name = "scheduler",
    description = "start an scheduler"
)
@Slf4j
public class SchedulerCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public SchedulerCommand() {
        super(true);
    }

    @Override
    public void run() {
        super.run();

        Scheduler scheduler = applicationContext.getBean(Scheduler.class);
        scheduler.run();

        log.info("Indexer started");

        Await.until(() -> !this.applicationContext.isRunning());
    }
}