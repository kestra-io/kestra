package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;
import jakarta.inject.Singleton;

@Singleton
public class NoOpStartupHook implements StartupHookInterface {
    @Override
    public void start(AbstractCommand abstractCommand) {

   }
}
