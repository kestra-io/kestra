package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.ExecutorInterface;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Await;
import picocli.CommandLine;

import java.util.Map;
import jakarta.inject.Inject;

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

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.EXECUTOR
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        ExecutorInterface executorService = applicationContext.getBean(ExecutorInterface.class);
        executorService.run();

        log.info("Executor started");

        this.shutdownHook(executorService::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
