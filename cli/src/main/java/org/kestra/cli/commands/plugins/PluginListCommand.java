package org.kestra.cli.commands.plugins;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.contexts.KestraClassLoader;
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
                "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        PluginScanner pluginScanner = new PluginScanner(KestraClassLoader.instance());
        List<RegisteredPlugin> scan = pluginScanner.scan(this.pluginsPath);

        scan.forEach(registeredPlugin -> log.info(registeredPlugin.toString()));
    }
}
