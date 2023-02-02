package io.kestra.cli.commands;

import io.kestra.cli.AbstractApiCommand;
import picocli.CommandLine;

import java.nio.file.Path;

public abstract class AbstractServiceNamespaceUpdateCommand extends AbstractApiCommand{
    @CommandLine.Parameters(index = "0", description = "the namespace of flow to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory containing files for current namespace")
    public Path directory;

    @CommandLine.Option(names = {"--no-delete"}, description = "if missing should not be deleted")
    public boolean noDelete = false ;
}
