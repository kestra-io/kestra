package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "executor",
    description = "start an executor"
)
@Slf4j
public class ExecutorCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

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
