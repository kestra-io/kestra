package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "scheduler",
    description = "start an scheduler"
)
@Slf4j
public class SchedulerCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

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
