package io.kestra.cli.commands;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "hello", description = "A command for testing launch without config")
public class HelloCommand extends AbstractCommand {

    @Override
    public Integer call() throws Exception {
        super.call();

        System.out.println("Hello from kestra");
        return 0;
    }
}
