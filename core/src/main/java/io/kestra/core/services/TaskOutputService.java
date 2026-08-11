package io.kestra.core.services;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.MapUtils;

import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Service to manage task outputs. It handles both saving and retrieving outputs, as well as the logic to decide
 * whether to store the output in the database or in an internal storage based on the configured limit.
 */
@Singleton
public class TaskOutputService {
    private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();

    private final TaskOutputRepositoryInterface outputRepository;
    private final StorageInterface storageInterface;
    private final NamespaceFactory namespaceFactory;
    private final int limit;

    public TaskOutputService(TaskOutputRepositoryInterface outputRepository, StorageInterface storageInterface, NamespaceFactory namespaceFactory,
        TaskOutputConfiguration taskOutputConfiguration) {
        this.outputRepository = outputRepository;
        this.storageInterface = storageInterface;
        this.namespaceFactory = namespaceFactory;
        this.limit = taskOutputConfiguration.limit();
    }

    /**
     * Save the outputs of a {@link TaskRunWithOutput}.
     * The outputs can be either stored directly in the database if they are below the configured limit, or in an internal storage if they exceed the limit.
     *
     * @see #saveOutputs(TaskRun, Map)
     * @see #saveOutputs(TaskRun, Output)
     */
    public void saveOutputs(TaskRunWithOutput taskRunWithOutput) throws InternalException {
        saveOutputs(taskRunWithOutput.taskRun(), taskRunWithOutput.outputs());
    }

    /**
     * Save the outputs of a task run.
     * The outputs can be either stored directly in the database if they are below the configured limit, or in an internal storage if they exceed the limit.
     *
     * @see #saveOutputs(TaskRun, Map)
     * @see #saveOutputs(TaskRunWithOutput)
     */
    public void saveOutputs(TaskRun taskRun, Output outputs) throws InternalException {
        Map<String, Object> outputMap = Optional.ofNullable(outputs).map(o -> o.toMap()).orElse(Collections.emptyMap());
        saveOutputs(taskRun, outputMap);
    }

    /**
     * Save the outputs of a task run from a map.
     *
     * @see #saveOutputs(TaskRun, Output)
     * @see #saveOutputs(TaskRunWithOutput)
     */
    public void saveOutputs(TaskRun taskRun, Map<String, Object> outputMap) throws InternalException {
        if (!MapUtils.isEmpty(outputMap)) {
            try {
                byte[] value = ION_MAPPER.writeValueAsBytes(outputMap);
                var output = shouldStoreInInternalStorage(value) ? storeToInternalStorage(taskRun, value)
                    : new TaskOutput(taskRun.getId(), taskRun.getTenantId(), taskRun.getExecutionId(), value, null);
                outputRepository.save(output);
            } catch (JsonProcessingException e) {
                throw new InternalException(e);
            }
        }
    }

    /**
     * Whether the value should be store in internal storage or not.
     * In OSS: this is only defined by the task output limit configuration if set.
     * In EE, this is also governed by the Execution data in internal storage configuration.
     */
    protected boolean shouldStoreInInternalStorage(byte[] value) {
        if (limit < 0) {
            return false;
        }
        return value.length > limit;
    }

    private TaskOutput storeToInternalStorage(TaskRun taskRun, byte[] outputBytes) throws InternalException {
        try {
            var context = StorageContext.forTask(taskRun);
            var storage = new InternalStorage(context, storageInterface, namespaceFactory);
            File file = Files.createTempFile("output-", ".ion").toFile();
            Files.write(file.toPath(), outputBytes);
            var uri = storage.putFile(file);
            return new TaskOutput(taskRun.getId(), taskRun.getTenantId(), taskRun.getExecutionId(), null, uri.toString());
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Get the outputs of a task run. This method will read the outputs from the database or from the internal storage depending on where they are stored.
     */
    @SuppressWarnings("deprecation")
    public Map<String, Object> getOutputs(TaskRun taskRun) throws InternalException {
        // pre 2.0 compatibility layer
        if (taskRun.getOutputs() != null) {
            return taskRun.getOutputs();
        }

        return outputRepository.findById(taskRun.getTenantId(), taskRun.getId())
            .map(throwFunction(output -> readOutput(taskRun, output)))
            .orElse(Collections.emptyMap());
    }

    /**
     * Compute the outputs of an execution.
     * This method will read all outputs of the execution and compute the final outputs for each task run as needed for the RunContext variables.
     */
    public Map<String, Object> computeOutputs(Execution execution) {
        if (execution == null || execution.getTaskRunList() == null) {
            return Collections.emptyMap();
        }

        // we pre-compute the map of taskrun by id to avoid traversing the list of all taskrun for each taskrun
        Map<String, TaskRun> byIds = execution.getTaskRunList().stream().collect(
            Collectors.toMap(
                taskRun -> taskRun.getId(),
                taskRun -> taskRun
            )
        );

        // load all outputs
        List<TaskOutput> allTaskOutputs = outputRepository.findByExecution(execution);

        Map<String, Object> result = new LinkedHashMap<>();
        execution.getTaskRunList().stream()
            .collect(Collectors.groupingBy(taskRun -> taskRun.getTaskId()))
            .forEach((taskId, taskRuns) ->
            {
                Map<String, Object> taskOutputs = new LinkedHashMap<>();
                for (TaskRun current : taskRuns) {
                    // pre 2.0 compatibility layer
                    @SuppressWarnings("deprecation")
                    Map<String, Object> outputMap = current.getOutputs();
                    if (outputMap == null) {
                        var outputs = allTaskOutputs.stream().filter(it -> it.taskRunId().equals(current.getId()))
                            .findAny();
                        if (outputs.isPresent()) {
                            try {
                                outputMap = readOutput(current, outputs.get());
                            } catch (InternalException e) {
                                throw new KestraRuntimeException(e);
                            }
                        }
                    }

                    if (outputMap != null) {
                        if (current.getIteration() != null) {
                            Map<String, Object> merged = MapUtils.merge(taskOutputs, outputs(current, outputMap, byIds));
                            // If one of two of the map is null in the merge() method, we just return the other
                            // And if the not null map is a Variables (= read-only), we cast it back to a simple
                            // hashmap to avoid taskOutputs becoming read-only
                            // i.e this happens in nested loopUntil tasks
                            if (merged instanceof Variables) {
                                merged = new HashMap<>(merged);
                            }
                            taskOutputs = merged;
                        } else {
                            taskOutputs.putAll(outputs(current, outputMap, byIds));
                        }
                    }
                }
                result.put(taskId, taskOutputs);
            });

        if (execution.getLoopRun() != null) {
            result.putAll(computeOutputs(execution.getLoopRun().parent()));
        }

        return result;
    }

    private Map<String, Object> readOutput(TaskRun taskRun, TaskOutput taskOutput) throws InternalException {
        try {
            return taskOutput.value() != null ? readFromValue(taskOutput) : readFromInternalStorage(taskRun, taskOutput);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    private Map<String, Object> readFromValue(TaskOutput taskOutput) throws IOException {
        return ION_MAPPER.readValue(taskOutput.value(), JacksonMapper.MAP_TYPE_REFERENCE);
    }

    private Map<String, Object> readFromInternalStorage(TaskRun taskRun, TaskOutput taskOutput) throws IOException {
        if (taskOutput.uri() == null) {
            return null;
        }

        var context = StorageContext.forTask(taskRun);
        var storage = new InternalStorage(context, storageInterface, namespaceFactory);
        try (var is = storage.getFile(URI.create(taskOutput.uri()))) {
            return ION_MAPPER.readValue(is, JacksonMapper.MAP_TYPE_REFERENCE);
        }
    }

    private Map<String, Object> outputs(TaskRun taskRun, Map<String, Object> outputs, Map<String, TaskRun> byIds) {
        List<TaskRun> parents = findParents(taskRun, byIds)
            .stream()
            .filter(r -> r.getValue() != null)
            .toList();

        if (parents.isEmpty()) {
            if (taskRun.getValue() == null) {
                return outputs;
            } else {
                return Map.of(taskRun.getValue(), outputs);
            }
        }

        Map<String, Object> result = LinkedHashMap.newLinkedHashMap(1);
        Map<String, Object> current = result;

        for (TaskRun t : parents) {
            HashMap<String, Object> item = LinkedHashMap.newLinkedHashMap(1);
            current.put(t.getValue(), item);
            current = item;
        }

        if (taskRun.getValue() != null) {
            current.put(taskRun.getValue(), outputs);
        } else {
            current.putAll(outputs);
        }

        return result;
    }

    /**
     * Find all parents from this {@link TaskRun}. This method does the same as #Execution.findParents(TaskRun
     * taskRun) but for performance reason, as it's called a lot, we pre-compute the map of taskrun
     * by ID and use it here.
     */
    private List<TaskRun> findParents(TaskRun taskRun, Map<String, TaskRun> taskRunById) {
        if (taskRun.getParentTaskRunId() == null || taskRunById.isEmpty()) {
            return Collections.emptyList();
        }

        List<TaskRun> result = new ArrayList<>();
        boolean ended = false;
        while (!ended) {
            final TaskRun finalTaskRun = taskRun;
            TaskRun find = taskRunById.get(finalTaskRun.getParentTaskRunId());

            if (find != null) {
                result.add(find);
                taskRun = find;
            } else {
                ended = true;
            }
        }

        Collections.reverse(result);

        return result;
    }

    /**
     * Purge (hard delete) task outputs for a given list of executions.
     *
     * @return the number of deleted outputs
     */
    public int purge(List<Execution> executions) {
        return this.outputRepository.purgeByExecutionIds(executions.stream().map(Execution::getId).toList());
    }

    public void copyOutputs(TaskRun originalTaskRun, TaskRun newTaskRun) {
        var previousOutput = outputRepository.findById(originalTaskRun.getTenantId(), originalTaskRun.getId());
        if (previousOutput.isPresent()) {
            var newOutput = new TaskOutput(newTaskRun.getId(), newTaskRun.getTenantId(), newTaskRun.getExecutionId(), previousOutput.get().value(), previousOutput.get().uri());
            outputRepository.save(newOutput);
        }
    }
}
