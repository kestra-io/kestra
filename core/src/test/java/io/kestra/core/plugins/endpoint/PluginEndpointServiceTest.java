package io.kestra.core.plugins.endpoint;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginEndpointServiceTest {
    private static final String TENANT = "main";
    private static final String CLS = "io.kestra.plugin.ai.SomeTask";
    private static final String EXEC = "exec1";
    private static final String TR = "tr1";
    private static final String NS = "io.kestra.test";
    private static final String FLOW = "myflow";

    private static Execution execution() {
        return Execution.builder()
            .tenantId(TENANT).namespace(NS).flowId(FLOW).id(EXEC)
            .taskRunList(List.of(TaskRun.builder().id(TR).outputs(Map.of("a", 1)).build()))
            .build();
    }

    private static PluginEndpoint endpoint(String name, AtomicReference<PluginEndpointContext> captured) {
        return new PluginEndpoint() {
            @Override public String name() { return name; }
            @Override public PluginEndpointResponse handle(PluginEndpointContext ctx) {
                captured.set(ctx);
                return PluginEndpointResponse.of(Map.of("ok", true));
            }
        };
    }

    private static PluginRegistry registryReturning(List<RegisteredPlugin> plugins) {
        PluginRegistry registry = mock(PluginRegistry.class);
        when(registry.plugins(any())).thenReturn(plugins);
        return registry;
    }

    private static RegisteredPlugin pluginWith(PluginEndpoint... endpoints) {
        RegisteredPlugin plugin = mock(RegisteredPlugin.class);
        lenient().when(plugin.getEndpoints()).thenReturn(List.of(endpoints));
        return plugin;
    }

    private static ExecutionRepositoryInterface execRepoReturning(Optional<Execution> execution) {
        ExecutionRepositoryInterface repo = mock(ExecutionRepositoryInterface.class);
        lenient().when(repo.findById(TENANT, EXEC)).thenReturn(execution);
        return repo;
    }

    private static PluginEndpointService service(PluginRegistry registry, ExecutionRepositoryInterface repo) {
        return new PluginEndpointService(registry, repo, mock(StorageInterface.class), mock(LogDataStoreInterface.class), mock(TaskOutputService.class));
    }

    @Test
    void shouldThrowNotFoundWhenClassUnknown() {
        PluginEndpointService service = service(registryReturning(List.of()), execRepoReturning(Optional.of(execution())));
        assertThatThrownBy(() -> service.dispatch(TENANT, "unknown", "hello", EXEC, TR, Map.of(), Map.of()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenEndpointNameUnknown() {
        AtomicReference<PluginEndpointContext> captured = new AtomicReference<>();
        PluginEndpointService service = service(
            registryReturning(List.of(pluginWith(endpoint("hello", captured)))),
            execRepoReturning(Optional.of(execution())));
        assertThatThrownBy(() -> service.dispatch(TENANT, CLS, "nope", EXEC, TR, Map.of(), Map.of()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenExecutionAbsent() {
        AtomicReference<PluginEndpointContext> captured = new AtomicReference<>();
        PluginEndpointService service = service(
            registryReturning(List.of(pluginWith(endpoint("hello", captured)))),
            execRepoReturning(Optional.empty()));
        assertThatThrownBy(() -> service.dispatch(TENANT, CLS, "hello", EXEC, TR, Map.of(), Map.of()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenTaskRunAbsent() {
        AtomicReference<PluginEndpointContext> captured = new AtomicReference<>();
        PluginEndpointService service = service(
            registryReturning(List.of(pluginWith(endpoint("hello", captured)))),
            execRepoReturning(Optional.of(execution())));
        assertThatThrownBy(() -> service.dispatch(TENANT, CLS, "hello", EXEC, "missing", Map.of(), Map.of()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldInvokeHandleWithScopedContext() throws Exception {
        AtomicReference<PluginEndpointContext> captured = new AtomicReference<>();
        TaskOutputService taskOutputService = mock(TaskOutputService.class);
        when(taskOutputService.getOutputs(any())).thenReturn(Map.of("a", 1));
        PluginEndpointService service = new PluginEndpointService(
            registryReturning(List.of(pluginWith(endpoint("hello", captured)))),
            execRepoReturning(Optional.of(execution())),
            mock(StorageInterface.class),
            mock(LogDataStoreInterface.class),
            taskOutputService);

        PluginEndpointResponse response = service.dispatch(
            TENANT, CLS, "hello", EXEC, TR, Map.of("name", List.of("toto")), Map.of("k", "v"));

        assertThat(response).isNotNull();
        PluginEndpointContext ctx = captured.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.executionId()).isEqualTo(EXEC);
        assertThat(ctx.taskRunId()).isEqualTo(TR);
        assertThat(ctx.param("name")).isEqualTo("toto");
        assertThat(ctx.param("missing")).isNull();
        assertThat(ctx.body()).containsExactlyEntriesOf(Map.of("k", "v"));
        assertThat(ctx.outputs().get()).containsExactlyEntriesOf(Map.of("a", 1));
    }

    @Test
    void shouldAuthorizeNamespaceBeforeInvokingHandle() {
        AtomicReference<PluginEndpointContext> captured = new AtomicReference<>();
        AtomicBoolean handleInvoked = new AtomicBoolean(false);
        AtomicReference<String> authorizedNamespace = new AtomicReference<>();

        PluginEndpoint endpoint = new PluginEndpoint() {
            @Override public String name() { return "hello"; }
            @Override public PluginEndpointResponse handle(PluginEndpointContext ctx) {
                handleInvoked.set(true);
                return PluginEndpointResponse.of(Map.of("ok", true));
            }
        };

        PluginEndpointService service = new PluginEndpointService(
            registryReturning(List.of(pluginWith(endpoint))),
            execRepoReturning(Optional.of(execution())),
            mock(StorageInterface.class),
            mock(LogDataStoreInterface.class),
            mock(TaskOutputService.class)
        ) {
            @Override protected void authorizeNamespace(String namespace) {
                authorizedNamespace.set(namespace);
                throw new SecurityException("denied");
            }
        };

        assertThatThrownBy(() -> service.dispatch(TENANT, CLS, "hello", EXEC, TR, Map.of(), Map.of()))
            .isInstanceOf(SecurityException.class);
        assertThat(authorizedNamespace.get()).isEqualTo(NS);
        assertThat(handleInvoked).isFalse();
    }

    @Test
    void shouldWrapHandlerFailure() {
        PluginEndpoint boom = new PluginEndpoint() {
            @Override public String name() { return "boom"; }
            @Override public PluginEndpointResponse handle(PluginEndpointContext ctx) {
                throw new RuntimeException("secret-detail-should-not-leak");
            }
        };
        PluginEndpointService service = service(
            registryReturning(List.of(pluginWith(boom))),
            execRepoReturning(Optional.of(execution())));

        assertThatThrownBy(() -> service.dispatch(TENANT, CLS, "boom", EXEC, TR, Map.of(), Map.of()))
            .isInstanceOf(PluginEndpointExecutionException.class)
            .hasMessageNotContaining("secret-detail-should-not-leak");
    }
}
