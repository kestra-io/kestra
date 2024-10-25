package io.kestra.cli.commands;

import io.kestra.cli.AbstractApiCommand;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import picocli.CommandLine;

import java.nio.file.Path;

public abstract class AbstractServiceNamespaceUpdateCommand extends AbstractApiCommand {
    @CommandLine.Parameters(index = "0", description = "the namespace to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory containing files for current namespace")
    public Path directory;

    @CommandLine.Option(names = {"--delete"}, negatable = true, description = "if missing should be deleted")
    public boolean delete = false;

}
