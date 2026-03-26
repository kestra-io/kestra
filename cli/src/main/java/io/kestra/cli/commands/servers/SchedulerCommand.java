package io.kestra.cli.commands.servers;

import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.utils.Await;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "scheduler",
    description = "Start the Kestra scheduler"
)
@Slf4j
public class SchedulerCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = { "-t", "--max-threads" }, description = "The maximum number of threads used by the scheduler for evaluating triggers.")
    private Integer maxThread;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.SCHEDULER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        Scheduler scheduler = applicationContext.getBean(Scheduler.class);
        scheduler.start(Optional.ofNullable(this.maxThread).orElse(Scheduler.defaultMaxNumThreads()));

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
