package org.floworc.core;

import io.micronaut.configuration.picocli.PicocliRunner;
import org.floworc.core.commands.TestCommand;
import org.floworc.core.commands.WorkerCommand;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "floworc",
    version = "v0.1",

    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n",

    mixinStandardHelpOptions = true,
    subcommands = {
        TestCommand.class,
        WorkerCommand.class
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
