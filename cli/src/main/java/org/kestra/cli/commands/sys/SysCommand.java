package org.kestra.cli.commands.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "sys",
    description = "handle systems maintenance",
    mixinStandardHelpOptions = true,
    subcommands = {
        RestoreQueueCommand.class,
    }
)
@Slf4j
public class SysCommand extends AbstractCommand {
    public SysCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "sys",  "--help");

        return 0;
    }
}
