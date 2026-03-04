package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.services.VersionService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class DefaultStartupHook implements StartupHookInterface {
   @Inject
   private ApplicationContext applicationContext;

   @Override
   public void start(AbstractCommand abstractCommand) {
      if (abstractCommand instanceof ServerCommandInterface){
         saveKestraVersion();
      }
   }

   private void saveKestraVersion() {
      applicationContext.findBean(VersionService.class).ifPresent(VersionService::maybeSaveOrUpdateInstanceVersion);
   }
}
