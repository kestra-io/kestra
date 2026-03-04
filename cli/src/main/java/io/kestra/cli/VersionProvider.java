package io.kestra.cli;

import io.kestra.core.contexts.KestraContext;
import picocli.CommandLine;

class VersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[]{KestraContext.getContext().getVersion()};
    }
}