package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;

public interface StartupHookInterface {
   void start(AbstractCommand abstractCommand);
}
