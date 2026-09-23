package io.kestra.cli.commands.servers;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import io.kestra.cli.StandAloneRunner;
import io.kestra.cli.services.StartupHookInterface;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.plugins.PluginManager;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.VersionProvider;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.env.Environment;
import jakarta.inject.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Guards the {@code server standalone --flow-path} lifecycle order from
 * https://github.com/kestra-io/kestra/issues/19736: external plugins must be registered
 * ({@code super.call()} → {@code maybeInitPlugins()}) before flows are loaded, otherwise
 * their task types fail to resolve and every flow is skipped.
 *
 * <p>
 * Runs the real {@link StandAloneCommand#call()} with mocked collaborators instead of a
 * blocking standalone server, so the order of plugin registration versus flow loading can be
 * asserted directly.
 * </p>
 */
class StandAloneCommandOrderTest {
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void shouldRegisterPluginsBeforeLoadingFlowsFromPath(@TempDir Path temp) throws Exception {
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        LocalFlowRepositoryLoader flowLoader = mock(LocalFlowRepositoryLoader.class);
        when(flowLoader.load(eq("main"), any(File.class))).thenReturn(List.of());

        TenantIdSelectorService tenantSelector = mock(TenantIdSelectorService.class);
        when(tenantSelector.getTenantId(any())).thenReturn("main");

        Provider<PluginRegistry> pluginRegistryProvider = mock(Provider.class);
        when(pluginRegistryProvider.get()).thenReturn(pluginRegistry);
        Provider<PluginManager> pluginManagerProvider = mock(Provider.class);
        when(pluginManagerProvider.get()).thenReturn(mock(PluginManager.class));
        Provider<LocalFlowRepositoryLoader> flowLoaderProvider = mock(Provider.class);
        when(flowLoaderProvider.get()).thenReturn(flowLoader);
        Provider<TenantIdSelectorService> tenantSelectorProvider = mock(Provider.class);
        when(tenantSelectorProvider.get()).thenReturn(tenantSelector);
        Provider<IgnoreExecutionService> ignoreExecutionProvider = mock(Provider.class);
        when(ignoreExecutionProvider.get()).thenReturn(mock(IgnoreExecutionService.class));
        Provider<StandAloneRunner> runnerProvider = mock(Provider.class);
        when(runnerProvider.get()).thenReturn(mock(StandAloneRunner.class));

        Environment environment = mock(Environment.class);
        when(environment.getActiveNames()).thenReturn(Set.of());
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(applicationContext.isRunning()).thenReturn(false);

        StandAloneCommand command = new StandAloneCommand();
        setField(command, "ignoreExecutionService", ignoreExecutionProvider);
        setField(command, "tenantIdSelectorService", tenantSelectorProvider);
        setField(command, "localFlowRepositoryLoader", flowLoaderProvider);
        setField(command, "standAloneRunnerProvider", runnerProvider);
        setField(command, "applicationContext", applicationContext);
        setField(command, "startupHook", mock(StartupHookInterface.class));
        setField(command, "versionProvider", mock(VersionProvider.class));
        setField(command, "embeddedServer", mock(BeanProvider.class));
        setField(command, "pluginRegistryProvider", pluginRegistryProvider);
        setField(command, "pluginManagerProvider", pluginManagerProvider);
        setField(command, "pluginsPath", temp);
        setField(command, "flowPath", temp.toFile());

        try (MockedStatic<KestraContext> kestraContext = mockStatic(KestraContext.class)) {
            KestraContext context = mock(KestraContext.class);
            kestraContext.when(KestraContext::getContext).thenReturn(context);

            String threadName = Thread.currentThread().getName();
            try {
                assertThat(command.call()).isZero();
            } finally {
                Thread.currentThread().setName(threadName);
            }
        }

        InOrder order = inOrder(pluginRegistry, flowLoader);
        order.verify(pluginRegistry).registerIfAbsent(any(Path.class));
        order.verify(flowLoader).load(eq("main"), any(File.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
