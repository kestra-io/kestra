package io.kestra.cli.services;

import java.util.Optional;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.servers.ServerCommandInterface;
import io.kestra.cli.commands.servers.WorkerCommand;
import io.kestra.core.mcp.services.McpServerService;
import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.services.VersionService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.EditionProvider;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultStartupHook implements StartupHookInterface {
    @Inject
    private BeanProvider<VersionService> versionService;

    @Inject
    private Optional<EditionProvider> editionProvider;

    @Inject
    private Optional<SettingRepositoryInterface> settingRepositoryProvider;

    @Inject
    BeanProvider<McpServerService> mcpServerService;

    @Inject
    BeanProvider<TenantService> tenantService;

    @Inject
    BeanProvider<PluginAutoInstallService> pluginAutoInstallService;

    @Override
    public void start(AbstractCommand cmd) {
        if (cmd instanceof ServerCommandInterface && !(cmd instanceof WorkerCommand)) {
            // Runs after the external plugins directory is registered (maybeInitPlugins) and before
            // any service resolves the lazy StorageInterface singleton, so a config-referenced
            // storage plugin missing from a slim distribution can still be installed in time.
            installConfigReferencedPlugins();
            saveKestraVersion();
            createDefaultMcpServerIfNotExist();
            saveKestraEdition();
        }
    }

    private void installConfigReferencedPlugins() {
        pluginAutoInstallService.ifPresent(PluginAutoInstallService::installMissingConfiguredPlugins);
    }

    private void saveKestraVersion() {
        versionService.ifPresent(VersionService::maybeSaveOrUpdateInstanceVersion);
    }

    private void createDefaultMcpServerIfNotExist() {
        mcpServerService.ifPresent(
            svc -> tenantService.ifPresent(
                ts -> ts.listTenants().forEach(svc::createDefaultMcpServerIfNotExist)
            )
        );
    }

    private void saveKestraEdition() {
        editionProvider.ifPresent(editionProvider -> settingRepositoryProvider.ifPresent(editionProvider::persistEdition));
    }
}
