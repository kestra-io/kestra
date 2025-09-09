package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "scheduler",
    description = "Start the Kestra scheduler"
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

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
