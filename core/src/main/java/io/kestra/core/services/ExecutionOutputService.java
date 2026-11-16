package io.kestra.core.services;

import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;
import io.kestra.core.searchable.SearchableDataRepositoryInterface;
import io.kestra.core.searchable.SearchableDataUtils;
import io.kestra.core.services.configuration.ExecutionOutputConfiguration;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import org.slf4j.Logger;

import jakarta.inject.Singleton;

/**
 * Service to manage the flow-level outputs of an execution. It handles both saving and retrieving outputs, as well as
 * the logic to decide whether to store the output in the database or in an internal storage based on the configured limit.
 */
@Singleton
public class ExecutionOutputService extends AbstractOutputService {
    private final ExecutionOutputRepositoryInterface outputRepository;
    private final SearchableDataRepositoryInterface searchableDataRepository;

    public ExecutionOutputService(ExecutionOutputRepositoryInterface outputRepository, SearchableDataRepositoryInterface searchableDataRepository, StorageInterface storageInterface, NamespaceFactory namespaceFactory,
                                  ExecutionOutputConfiguration executionOutputConfiguration) {
        super(storageInterface, namespaceFactory, executionOutputConfiguration.limit());
        this.outputRepository = outputRepository;
        this.searchableDataRepository = searchableDataRepository;
    }

    /**
     * Save the outputs of an execution.
     * The outputs can be either stored directly in the database if they are below the configured limit or in an internal storage if they exceed the limit.
     * <p>
     * A searchable output is also indexed in the dedicated table, a failure to index it is logged and doesn't fail the save.
     */
    public void saveOutputs(Logger logger, FlowInterface flow, Execution execution, Map<String, Object> outputMap) throws InternalException {
        if (!MapUtils.isEmpty(outputMap)) {
            byte[] value = serialize(outputMap);
            var output = shouldStoreInInternalStorage(value)
                ? new ExecutionOutput(execution.getId(), execution.getTenantId(), null, storeToInternalStorage(StorageContext.forExecution(execution), value).toString())
                : new ExecutionOutput(execution.getId(), execution.getTenantId(), value, null);
            outputRepository.save(output);

            saveSearchableOutputs(logger, flow, execution, outputMap);
        }
    }

    private void saveSearchableOutputs(Logger logger, FlowInterface flow, Execution execution, Map<String, Object> outputMap) {
        ListUtils.emptyOnNull(flow.getOutputs()).stream()
            .filter(it -> Boolean.TRUE.equals(it.getSearchable()))
            .forEach(it -> {
                try {
                    searchableDataRepository.saveBatch(SearchableDataUtils.toSearchableData(logger, execution, it, outputMap.get(it.getId())));
                } catch (Exception e) {
                    logger.error("Failed to index the searchable output '{}' of the execution '{}', it will not be searchable: {}", it.getId(), execution.getId(), e.getMessage(), e);
                }
            });
    }

    /**
     * Get the outputs of an execution. This method will read the outputs from the database or from the internal storage depending on where they are stored.
     */
    @SuppressWarnings("deprecation")
    public Map<String, Object> getOutputs(Execution execution) throws InternalException {
        if (execution == null) {
            return null;
        }

        // pre 2.0 compatibility layer
        if (execution.getOutputs() != null) {
            return execution.getOutputs();
        }

        // outputs are only computed when an execution terminates, so we avoid a database round trip on the
        // hot path that creates a run context for each task run of a running execution
        if (execution.getState() == null || !execution.getState().isTerminated()) {
            return null;
        }

        var output = outputRepository.findById(execution.getTenantId(), execution.getId());
        if (output.isEmpty()) {
            return null;
        }

        return MapUtils.emptyOnNull(read(() -> StorageContext.forExecution(execution), output.get().value(), output.get().uri()));
    }

    /**
     * Purge (hard delete) execution outputs for a given list of executions.
     *
     * @return the number of deleted outputs
     */
    public int purge(List<Execution> executions) {
        return this.outputRepository.purgeByExecutionIds(executions.stream().map(Execution::getId).toList());
    }
}
