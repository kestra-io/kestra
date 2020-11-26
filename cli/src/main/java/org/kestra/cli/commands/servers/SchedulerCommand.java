package org.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.ServerType;
import org.kestra.core.schedulers.AbstractScheduler;
import org.kestra.core.utils.Await;
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

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
