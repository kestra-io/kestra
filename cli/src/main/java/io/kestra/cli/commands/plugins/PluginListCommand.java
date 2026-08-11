package io.kestra.cli.commands.plugins;

import java.util.List;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.plugins.RegisteredPlugin;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "list",
    description = "List all plugins already installed"
)
public class PluginListCommand extends AbstractCommand {
    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = { "--core" }, description = "Also write core tasks plugins")
    private boolean core = false;

    @Inject
    ApplicationContext applicationContext; // force injection of beans in AbstractCommand

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.pluginsPath == null) {
            throw new CommandLine.ParameterException(
                this.spec.commandLine(), "Missing required options '--plugins' " +
                    "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        List<RegisteredPlugin> plugins = core ? pluginRegistry.plugins() : pluginRegistry.externalPlugins();
        plugins.forEach(registeredPlugin -> stdOut(registeredPlugin.toString()));

        return 0;
    }
}
