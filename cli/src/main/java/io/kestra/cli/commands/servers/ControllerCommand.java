package io.kestra.cli.commands.servers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.ServerType;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Await;
import io.kestra.core.worker.Controller;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "controller",
    description = "Start the Kestra a controller for workers"
)
public class ControllerCommand extends AbstractServerCommand {

    @CommandLine.Option(names = { "--ignore-queue-records" }, split = ",", description = "a list of queue record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreQueueRecords = Collections.emptyList();

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return Map.of(
            "kestra.server-type", ServerType.CONTROLLER
        );
    }

    @Override
    public Integer call() throws Exception {
        this.ignoreExecutionService.setIgnoredQueueRecords(ignoreQueueRecords);

        super.call();

        Controller controller = applicationContext.getBean(Controller.class);
        controller.start();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
