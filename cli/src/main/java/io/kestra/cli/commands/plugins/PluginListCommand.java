package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
    name = "list",
    description = "list all plugins already installed"
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

        PluginScanner pluginScanner = new PluginScanner(KestraClassLoader.instance());
        List<RegisteredPlugin> scan = pluginScanner.scan(this.pluginsPath);

        if (core) {
            PluginScanner corePluginScanner = new PluginScanner(PluginDocCommand.class.getClassLoader());
            scan.add(corePluginScanner.scan());
        }

        scan.forEach(registeredPlugin -> stdOut(registeredPlugin.toString()));

        return 0;
    }
}
