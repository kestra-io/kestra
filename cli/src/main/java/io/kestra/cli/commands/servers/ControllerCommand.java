package io.kestra.cli.commands.servers;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Await;
import io.kestra.core.worker.Controller;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

import java.util.Map;

@Command(
    name = "controller",
    description = "Start the Kestra a controller for workers"
)
public class ControllerCommand extends AbstractServerCommand {

    @Inject
    private Controller controller;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return Map.of(
            "kestra.server-type", ServerType.CONTROLLER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        controller.start();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
