package org.kestra.cli.commands.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "sys",
    description = "handle systems maintenance",
    mixinStandardHelpOptions = true,
    subcommands = {
        RestoreFlowQueueCommand.class,
    }
)
@Slf4j
public class SysCommand extends AbstractCommand {
    public SysCommand() {
        super(false);
    }

    @SneakyThrows
    @Override
    public void run() {
        super.run();

        PicocliRunner.call(App.class, "sys",  "--help");
    }
}
