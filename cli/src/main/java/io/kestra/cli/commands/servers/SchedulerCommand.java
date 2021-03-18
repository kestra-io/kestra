package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.utils.Await;
import picocli.CommandLine;

import java.util.Map;
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

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.SCHEDULER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        AbstractScheduler scheduler = applicationContext.getBean(AbstractScheduler.class);
        scheduler.run();

        log.info("Scheduler started");

        this.shutdownHook(scheduler::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
