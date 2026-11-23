package io.kestra.cli.commands.configs.sys;

import io.kestra.cli.AbstractCommand;

/**
 * Base class for the {@code configs} commands. None of them resolves a plugin, so external plugin
 * loading is disabled: without it, {@code configs properties} and {@code configs validate} scan the
 * core plugins and the external plugins directory whenever {@code KESTRA_PLUGINS_PATH} is set, which
 * is the case in the Docker image.
 */
public abstract class AbstractConfigCommand extends AbstractCommand {

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
