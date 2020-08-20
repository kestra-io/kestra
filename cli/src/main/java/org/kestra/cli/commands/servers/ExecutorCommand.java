package org.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.ServerType;
import org.kestra.core.runners.AbstractExecutor;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import java.util.Map;
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

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.EXECUTOR
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        AbstractExecutor abstractExecutor = applicationContext.getBean(AbstractExecutor.class);
        abstractExecutor.run();

        log.info("Executor started");

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
