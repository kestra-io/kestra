package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.cli.commands.servers.WorkerCommand;
import io.kestra.core.services.VersionService;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultStartupHook implements StartupHookInterface {
    @Inject
    private ApplicationContext applicationContext;

    @Override
    public void start(AbstractCommand cmd) {
        if (cmd instanceof ServerCommandInterface && !(cmd instanceof WorkerCommand)) {
            saveKestraVersion();
        }
    }

    private void saveKestraVersion() {
        applicationContext.findBean(VersionService.class).ifPresent(VersionService::maybeSaveOrUpdateInstanceVersion);
    }
}
