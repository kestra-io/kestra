package io.kestra.cli.commands;

import io.kestra.cli.AbstractApiCommand;
import picocli.CommandLine;

import java.nio.file.Path;

public abstract class AbstractServiceNamespaceUpdateCommand extends AbstractApiCommand {
    @CommandLine.Parameters(index = "0", description = "The namespace to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "The directory containing flow files for current namespace")
    public Path directory;

    @CommandLine.Option(names = {"--delete"}, negatable = true, description = "Whether missing should be deleted")
    public boolean delete = false;

}
