package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.List;

@Command(
    name = "list",
    description = "List all plugins already installed"
)
public class PluginListCommand extends AbstractCommand {
    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"--core"}, description = "Also write core tasks plugins")
    private boolean core = false;

    @Inject
    private PluginRegistry registry;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.pluginsPath == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Missing required options '--plugins' " +
                "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        List<RegisteredPlugin> plugins = core ? registry.plugins() : registry.externalPlugins();

        plugins.forEach(registeredPlugin -> stdOut(registeredPlugin.toString()));

        return 0;
    }
}
