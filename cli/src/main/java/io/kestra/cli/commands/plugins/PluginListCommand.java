package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.plugins.RegisteredPlugin;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
    name = "list",
    description = "List all plugins already installed"
)
public class PluginListCommand extends AbstractCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--core"}, description = "Also write core tasks plugins")
    private boolean core = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.pluginsPath == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Missing required options '--plugins' " +
                "or environment variable 'KESTRA_PLUGINS_PATH"
            );
        }

        List<RegisteredPlugin> plugins = core ?  pluginRegistry().plugins() :  pluginRegistry().externalPlugins();
        plugins.forEach(registeredPlugin -> stdOut(registeredPlugin.toString()));

        return 0;
    }
}
