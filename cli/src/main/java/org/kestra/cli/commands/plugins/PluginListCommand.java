package org.kestra.cli.commands.plugins;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.contexts.KestraClassLoader;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
    name = "list",
    description = "list all plugins already installed"
)
@Slf4j
public class PluginListCommand extends AbstractCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public PluginListCommand() {
        super(false);
    }

    @CommandLine.Option(names = {"--core"}, description = "Also write core tasks plugins")
    private boolean core = false;

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

        if (core) {
            PluginScanner corePluginScanner = new PluginScanner(PluginDocCommand.class.getClassLoader());
            scan.add(corePluginScanner.scan());
        }

        scan.forEach(registeredPlugin -> log.info(registeredPlugin.toString()));
    }
}
