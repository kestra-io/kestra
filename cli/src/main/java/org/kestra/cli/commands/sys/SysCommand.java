package org.kestra.cli.commands.sys;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
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
}
