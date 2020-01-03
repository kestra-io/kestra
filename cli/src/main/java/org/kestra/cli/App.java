package org.kestra.cli;

import io.micronaut.configuration.picocli.PicocliRunner;
import org.kestra.cli.commands.plugins.PluginCommand;
import org.kestra.cli.commands.servers.StandAloneCommand;
import org.kestra.cli.commands.TestCommand;
import org.kestra.cli.commands.servers.WebServerCommand;
import org.kestra.cli.commands.servers.WorkerCommand;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "kestra",
    version = "v0.1",

    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n",

    mixinStandardHelpOptions = true,
    subcommands = {
        StandAloneCommand.class,
        TestCommand.class,
        WebServerCommand.class,
        WorkerCommand.class,
        PluginCommand.class
    }
)
public class App implements Callable<Object> {
    public static void main(String[] args) throws Exception {
        PicocliRunner.call(App.class, args);
    }

    @Override
    public Object call() throws Exception {
        return PicocliRunner.call(App.class, "--help");
    }
}
