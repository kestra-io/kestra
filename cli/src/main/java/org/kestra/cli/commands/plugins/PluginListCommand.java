package org.kestra.cli.commands.plugins;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.List;

@CommandLine.Command(
    name = "list",
    description = "list all plugins already installed"
)
@Slf4j
public class PluginListCommand extends AbstractCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    public PluginListCommand() {
        super(false);
    }

    @Override
    public void run() {
        super.run();

        if (this.pluginsPath == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Missing required options '--plugins' " +
                "or environnement variable 'KESTRA_PLUGINS_PATH"
            );
        }

        PluginScanner pluginScanner = new PluginScanner();
        List<RegisteredPlugin> scan = pluginScanner.scan(this.pluginsPath);

        scan.forEach(registeredPlugin -> {
            log.info("Found plugin on path: {}", registeredPlugin.getExternalPlugin().getLocation());

            if (!registeredPlugin.getTasks().isEmpty()) {
                log.info("Tasks:");
                registeredPlugin.getTasks().forEach(cls -> log.info("- {}", cls.getName()));
            }

            if (!registeredPlugin.getConditions().isEmpty()) {
                log.info("Condition:");
                registeredPlugin.getConditions().forEach(cls -> log.info("- {}", cls.getName()));
            }

            if (!registeredPlugin.getControllers().isEmpty()) {
                log.info("Controllers:");
                registeredPlugin.getControllers().forEach(cls -> log.info("- {}", cls.getName()));
            }

            if (!registeredPlugin.getStorages().isEmpty()) {
                log.info("Storages:");
                registeredPlugin.getStorages().forEach(cls -> log.info("- {}", cls.getName()));
            }

            log.info("");
        });
    }
}
