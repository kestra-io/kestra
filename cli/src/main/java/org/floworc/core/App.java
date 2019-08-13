package org.floworc.core;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "floworc",
    version = "v0.1",
    header = "floworc client",
    mixinStandardHelpOptions = true,
    subcommands = {

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
