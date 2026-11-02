package io.kestra.core.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;
import io.kestra.core.searchable.SearchableOutput;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;
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
    /** The size in characters of the {@code searchable_outputs.value} column. */
    static final int MAX_SEARCHABLE_VALUE_LENGTH = 2_048;
    static final int MAX_SEARCHABLE_ARRAY_ITEMS = 1_000;

    private final ExecutionOutputRepositoryInterface outputRepository;
    private final SearchableOutputRepositoryInterface searchableOutputRepository;

    public ExecutionOutputService(ExecutionOutputRepositoryInterface outputRepository, SearchableOutputRepositoryInterface searchableOutputRepository, StorageInterface storageInterface, NamespaceFactory namespaceFactory,
        ExecutionOutputConfiguration executionOutputConfiguration) {
        super(storageInterface, namespaceFactory, executionOutputConfiguration.limit());
        this.outputRepository = outputRepository;
        this.searchableOutputRepository = searchableOutputRepository;
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
                    searchableOutputRepository.saveBatch(toSearchableOutputs(logger, execution, it, outputMap.get(it.getId())));
                } catch (Exception e) {
                    logger.error("Failed to index the searchable output '{}' of the execution '{}', it will not be searchable: {}", it.getId(), execution.getId(), e.getMessage(), e);
                }
            });
    }

    private List<SearchableOutput> toSearchableOutputs(Logger logger, Execution execution, Output output, Object value) {
        List<String> values = toSearchableValues(logger, execution, output, value);
        return IntStream.range(0, values.size())
            .mapToObj(index -> new SearchableOutput(execution.getTenantId(), execution.getId(), output.getId(), index, values.get(index)))
            .toList();
    }

    /**
     * An array output is searchable item by item, any other output as a single value, and an absent output has none.
     * An array is truncated to {@value #MAX_SEARCHABLE_ARRAY_ITEMS} items and a value to {@value #MAX_SEARCHABLE_VALUE_LENGTH} characters.
     */
    private List<String> toSearchableValues(Logger logger, Execution execution, Output output, Object value) {
        if (value == null) {
            return List.of();
        }

        if (Type.ARRAY == output.getType()) {
            if (!(value instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("The value of the searchable array output '%s' is not a list.".formatted(output.getId()));
            }

            List<?> items = collection.stream().filter(Objects::nonNull).toList();
            if (items.size() > MAX_SEARCHABLE_ARRAY_ITEMS) {
                logger.warn("The searchable array output '{}' of the execution '{}' has {} items, only the first {} are searchable.",
                    output.getId(), execution.getId(), items.size(), MAX_SEARCHABLE_ARRAY_ITEMS);
                items = items.subList(0, MAX_SEARCHABLE_ARRAY_ITEMS);
            }

            List<String> values = items.stream().map(item -> toSearchableValue(output, item, true)).toList();
            long tooLong = values.stream().filter(it -> it.length() > MAX_SEARCHABLE_VALUE_LENGTH).count();
            if (tooLong > 0) {
                logger.warn("{} values of the searchable array output '{}' of the execution '{}' are longer than {} characters, they are truncated.",
                    tooLong, output.getId(), execution.getId(), MAX_SEARCHABLE_VALUE_LENGTH);
            }
            return values.stream().map(ExecutionOutputService::truncate).toList();
        }

        String single = toSearchableValue(output, value, false);
        if (single.length() > MAX_SEARCHABLE_VALUE_LENGTH) {
            logger.warn("The value of the searchable output '{}' of the execution '{}' is longer than {} characters, it is truncated.",
                output.getId(), execution.getId(), MAX_SEARCHABLE_VALUE_LENGTH);
        }
        return List.of(truncate(single));
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_SEARCHABLE_VALUE_LENGTH) {
            return value;
        }

        int end = MAX_SEARCHABLE_VALUE_LENGTH;
        // do not cut a surrogate pair in half
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private String toSearchableValue(Output output, Object value, boolean arrayItem) {
        if (arrayItem && (value instanceof Collection<?> || value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Only scalar items are supported for the searchable array output '%s'.".formatted(output.getId()));
        }

        if (!Output.SEARCHABLE_TYPES.contains(output.getType())) {
            throw new IllegalArgumentException("The output '%s' cannot be searchable because its type %s is not supported.".formatted(output.getId(), output.getType()));
        }

        return value.toString();
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
