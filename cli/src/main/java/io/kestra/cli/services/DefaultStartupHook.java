package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
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
      applicationContext.findBean(SettingRepositoryInterface.class).ifPresent(repository -> {
         Optional<Setting> versionSetting = repository.findByKey(Setting.INSTANCE_VERSION);
         final String version = KestraContext.getContext().getVersion();
         if (versionSetting.isEmpty() || !versionSetting.get().getValue().equals(version)) {
            repository.save(Setting.builder()
                .key(Setting.INSTANCE_VERSION)
                .value(version)
                .build()
            );
         }
      });
   }
}
