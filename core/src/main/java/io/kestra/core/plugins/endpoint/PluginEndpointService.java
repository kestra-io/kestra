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
import io.kestra.core.utils.ListUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class PluginEndpointService {
    private final PluginRegistry pluginRegistry;
    private final ExecutionRepositoryInterface executionRepository;
    private final StorageInterface storageInterface;
    private final LogDataStoreInterface logDataStore;
    private final TaskOutputService taskOutputService;

    @Inject
    public PluginEndpointService(
        PluginRegistry pluginRegistry,
        ExecutionRepositoryInterface executionRepository,
        StorageInterface storageInterface,
        LogDataStoreInterface logDataStore,
        TaskOutputService taskOutputService
    ) {
        this.pluginRegistry = pluginRegistry;
        this.executionRepository = executionRepository;
        this.storageInterface = storageInterface;
        this.logDataStore = logDataStore;
        this.taskOutputService = taskOutputService;
    }

    public PluginEndpointResponse dispatch(
        String tenantId,
        String cls,
        String name,
        String executionId,
        String taskRunId,
        Map<String, List<String>> parameters,
        Map<String, Object> body
    ) {
        // Authorize access to the execution before any plugin lookup: the caller must be allowed to read
        // this execution (and its namespace) before we reveal whether a plugin or endpoint exists.
        Execution execution = executionRepository.findById(tenantId, executionId)
            .orElseThrow(() -> new NotFoundException("No execution '%s' found.".formatted(executionId)));

        String namespace = execution.getNamespace();

        authorizeNamespace(namespace);

        TaskRun taskRun = execution.findTaskRunByTaskRunIdIfPresent(taskRunId)
            .orElseThrow(() -> new NotFoundException("No taskRun '%s' found in execution '%s'.".formatted(taskRunId, executionId)));

        RegisteredPlugin plugin = pluginRegistry.plugins(p -> p.hasClass(cls))
            .stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No plugin found for class '%s'.".formatted(cls)));

        PluginEndpoint endpoint = ListUtils.emptyOnNull(plugin.getEndpoints()).stream()
            .filter(e -> e.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No endpoint '%s' for plugin class '%s'.".formatted(name, cls)));

        TaskRunLogsFetcher logs = new DefaultTaskRunLogsFetcher(logDataStore, tenantId, executionId, taskRunId);
        TaskRunOutputsFetcher outputs = new DefaultTaskRunOutputsFetcher(taskOutputService, taskRun);
        ScopedStorage storage = new ScopedExecutionStorage(storageInterface, tenantId, namespace, execution.getFlowId(), executionId);

        PluginEndpointContext context = new DefaultPluginEndpointContext(parameters, body, executionId, taskRunId, logs, outputs, storage);

        try {
            return endpoint.handle(context);
        } catch (Exception e) {
            throw new PluginEndpointExecutionException(cls, name, e);
        }
    }

    /**
     * Authorization hook run once the execution's namespace is known and before the plugin is invoked.
     * No-op in OSS; Enterprise Edition overrides it to enforce the namespace-scoped grants.
     */
    protected void authorizeNamespace(String namespace) {
    }
}
