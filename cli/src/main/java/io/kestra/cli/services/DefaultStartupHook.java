package io.kestra.cli.services;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.cli.commands.servers.WorkerCommand;
import io.kestra.core.mcp.services.McpServerService;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.services.VersionService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class DefaultStartupHook implements StartupHookInterface {
    @Inject
    private BeanProvider<VersionService> versionService;

    @Inject
    private Optional<EditionProvider> editionProvider;

    @Inject
    private Optional<SettingRepositoryInterface> settingRepositoryProvider;

    @Inject
    Optional<McpServerService> mcpServerService;

    @Inject
    Optional<TenantService> tenantService;

    @Override
    public void start(AbstractCommand cmd) {
        if (cmd instanceof ServerCommandInterface && !(cmd instanceof WorkerCommand)) {
            saveKestraVersion();
            createDefaultMcpServerIfNotExist();
            saveKestraEdition();
        }
    }

   private void saveKestraVersion() {
      versionService.ifPresent(VersionService::maybeSaveOrUpdateInstanceVersion);
   }

    private void createDefaultMcpServerIfNotExist() {
        mcpServerService.ifPresent(svc ->
            tenantService.ifPresent(
                ts -> ts.listTenants().forEach(svc::createDefaultMcpServerIfNotExist)
            )
        );
    }

    private void saveKestraEdition() {
        editionProvider.ifPresent(editionProvider -> settingRepositoryProvider.ifPresent(editionProvider::persistEdition));
    }
}
