package io.kestra.cli.commands.servers;

import java.util.Map;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Await;
import io.kestra.core.worker.Controller;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

@Command(
    name = "controller",
    description = "Start the Kestra a controller for workers"
)
public class ControllerCommand extends AbstractServerCommand {

    @Inject
    private ApplicationContext applicationContext;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return Map.of(
            "kestra.server-type", ServerType.CONTROLLER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        Controller controller = applicationContext.getBean(Controller.class);
        controller.start();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
