package io.kestra.core.storages;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.Slugify;
import jakarta.annotation.Nullable;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Context used for storing and retrieving data from Kestra's storage.
 */
@Getter
public class StorageContext {

    public static final String KESTRA_SCHEME = "kestra";

    // /{namespace}/_files
    static final String PREFIX_FORMAT_NAMESPACE_FILE = "/%s/_files";
    // /{namespace}/{flow-id}
    static final String PREFIX_FORMAT_FLOWS = "/%s/%s";
    // /{namespace}/{flow-id}/executions/{execution-id}
    static final String PREFIX_FORMAT_EXECUTIONS = "/%s/%s/executions/%s";
    // /{namespace}/{flow-id}/executions/{execution-id}/tasks/{task-id}/{task-run-id}
    static final String PREFIX_FORMAT_TASK = "/%s/%s/executions/%s/tasks/%s/%s";
    // /{namespace}/{flow-id}/executions/{execution-id}/trigger/{trigger-id}
    static final String PREFIX_FORMAT_TRIGGER = "/%s/%s/executions/%s/trigger/%s";
    // /{namespace}/{flow-id}/executions/{execution-id}/inputs/{input-name}/{file-name}
    static final String PREFIX_FORMAT_INPUTS = "/%s/%s/executions/%s/inputs/%s/%s";
    // /{namespace}/{flow-id}/{cache-id}/cache/{object-id}/cache.zip
    static final String PREFIX_FORMAT_CACHE_OBJECT = "/%s/%s/%s/cache/%s/cache.zip";
    // /{namespace}/{flow-id}/{cache-id}/cache/cache.zip
    static final String PREFIX_FORMAT_CACHE = "/%s/%s/%s/cache/cache.zip";

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given {@link TaskRun}.
     */
    public static StorageContext forTask(TaskRun taskRun) {
        return new StorageContext.Task(
            taskRun.getTenantId(),
            taskRun.getNamespace(),
            taskRun.getFlowId(),
            taskRun.getExecutionId(),
            taskRun.getTaskId(),
            taskRun.getId(),
            taskRun.getValue()
        );
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given {@link Flow}.
     */
    public static StorageContext forFlow(Flow flow) {
        return new StorageContext(flow.getTenantId(), flow.getNamespace(), flow.getId());
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given {@link Execution}.
     */
    public static StorageContext forExecution(Execution execution) {
        return forExecution(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId());
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given Execution.
     */
    public static StorageContext forExecution(@Nullable String tenantId,
                                              String namespace,
                                              String flowId,
                                              String executionId) {
        return new StorageContext(tenantId, namespace, flowId, executionId);
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given {@link Execution} and input.
     */
    public static StorageContext.Input forInput(Execution execution,
                                                String inputName,
                                                String fileName) {
        return new StorageContext.Input(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), inputName, fileName);
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given Task.
     */
    public static StorageContext.Task forTask(@Nullable String tenantId,
                                              String namespace,
                                              String flowId,
                                              String executionId,
                                              String taskId,
                                              String taskRunId,
                                              @Nullable String taskRunValue) {
        return new StorageContext.Task(tenantId, namespace, flowId, executionId, taskId, taskRunId, taskRunValue);
    }

    /**
     * Factory method for constructing a new {@link StorageContext} scoped to a given Trigger.
     */
    public static StorageContext.Trigger forTrigger(@Nullable String tenantId,
                                                    String namespace,
                                                    String flowId,
                                                    String executionId,
                                                    String triggerId) {
        return new StorageContext.Trigger(tenantId, namespace, flowId, executionId, triggerId);
    }

    private final String tenantId;
    private final String namespace;
    private final String flowId;
    private final String executionId;

    @VisibleForTesting
    public StorageContext() {
        this.tenantId = null;
        this.namespace = null;
        this.flowId = null;
        this.executionId = null;
    }

    private StorageContext(final @Nullable String tenantId,
                           final String namespace,
                           final String flowId) {
        this.tenantId = tenantId;
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.flowId = Objects.requireNonNull(flowId, "flowId cannot be null");
        this.executionId = null;
    }

    private StorageContext(final @Nullable String tenantId,
                           final String namespace,
                           final String flowId,
                           final String executionId) {
        this.tenantId = tenantId;
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.flowId = Objects.requireNonNull(flowId, "flowId cannot be null");
        this.executionId = Objects.requireNonNull(executionId, "executionId cannot be null");
    }

    public static Optional<String> extractExecutionId(URI path) {
        Pattern pattern = Pattern.compile("^/(.+)/executions/([^/]+)/", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(path.getPath());

        if (!matcher.find() || matcher.group(2).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(2));
    }

    /**
     * Gets the storage URI of the given cacheID, and optionally the given objectID.
     *
     * @param cacheId  the ID of the cache.
     * @param objectId the ID object cached object (optional).
     * @return the URI
     */
    public URI getCacheURI(final String cacheId, @Nullable final String objectId) {
        Objects.requireNonNull(cacheId, "Cannot create URI with id null");

        final String prefix;
        if (objectId == null) {
            prefix = String.format(
                PREFIX_FORMAT_CACHE,
                getNamespaceAsPath(),
                Slugify.of(getFlowId()),
                Slugify.of(cacheId)
            );
        } else {
            String hashedObjectId = Hashing.hashToString(objectId);
            prefix = String.format(
                PREFIX_FORMAT_CACHE_OBJECT,
                getNamespaceAsPath(),
                Slugify.of(getFlowId()),
                Slugify.of(cacheId),
                hashedObjectId
            );
        }
        return URI.create(prefix);
    }

    public String getNamespaceAsPath() {
        return getNamespace().replace(".", "/");
    }

    /**
     * Gets the storage prefix for the given state store ID.
     *
     * @param id          the primary ID of the state.
     * @param isNamespace specify whether the state is on namespace or flow level.
     * @param value       the secondary ID (e.g., the runTaskValue).
     * @return the storage prefix.
     */
    public String getStateStorePrefix(String id, Boolean isNamespace, String value) {
        ArrayList<String> paths = new ArrayList<>(List.of(getNamespaceAsPath()));

        if (!isNamespace) {
            paths.add(Slugify.of(getFlowId()));
        }

        paths.add("states");

        if (id != null) {
            paths.add(id);
        }

        if (value != null) {
            paths.add(Hashing.hashToString(value));
        }

        return "/" + String.join("/", paths);
    }

    /**
     * Gets the base storage URI for the current {@link io.kestra.core.models.flows.Flow}.
     *
     * @return the {@link URI}.
     */
    public URI getFlowStorageURI() {
        try {
            var prefix = String.format(PREFIX_FORMAT_FLOWS, getNamespaceAsPath(), Slugify.of(getFlowId()));
            return new URI("//" + prefix);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Gets the base storage URI for the current {@link Execution}.
     *
     * @return the {@link URI}.
     */
    public URI getExecutionStorageURI() {
        return getExecutionStorageURI(null);
    }

    /**
     * Gets the base storage URI for the current {@link Execution}.
     *
     * @param scheme The scheme name.
     * @return the {@link URI}.
     */
    public URI getExecutionStorageURI(@Nullable String scheme) {
        try {
            var schemePrefix = Optional.ofNullable(scheme)
                .map(s -> s.endsWith("://") ? s : s + "://")
                .orElse("//");

            var prefix = String.format(PREFIX_FORMAT_EXECUTIONS,
                getNamespaceAsPath(),
                Slugify.of(flowId),
                executionId
            );
            return new URI(schemePrefix + prefix);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Gets the base storage URI for this context.
     *
     * @return the {@link URI}.
     */
    public URI getContextStorageURI() {
        return getExecutionStorageURI();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public String toString() {
        return "StorageContext::Execution";
    }


    public static String namespaceFilePrefix(String namespace) {
        return String.format(PREFIX_FORMAT_NAMESPACE_FILE, namespace.replace(".", "/"));
    }

    /**
     * A storage context scoped to a Task.
     */
    @Getter
    public static class Task extends StorageContext {

        private final String taskId;
        private final String taskRunId;
        private final String taskRunValue;

        private Task(final String tenantId,
                     final String namespace,
                     final String flowId,
                     final String executionId,
                     final String taskId,
                     final String taskRunId,
                     @Nullable final String taskRunValue) {
            super(tenantId, namespace, flowId, executionId);
            this.taskId = Objects.requireNonNull(taskId, "taskID cannot be null");
            this.taskRunId = Objects.requireNonNull(taskRunId, "taskRunID cannot be null");
            this.taskRunValue = taskRunValue;
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public URI getContextStorageURI() {
            try {
                var prefix = String.format(
                    PREFIX_FORMAT_TASK,
                    getNamespaceAsPath(),
                    Slugify.of(getFlowId()),
                    getExecutionId(),
                    Slugify.of(getTaskId()),
                    getTaskRunId()
                );
                return new URI("//" + prefix);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public String toString() {
            return "StorageContext::Task";
        }
    }

    /**
     * A storage context scoped to a Trigger.
     */
    @Getter
    public static class Trigger extends StorageContext {


        private final String triggerId;

        private Trigger(final String tenantId,
                        final String namespace,
                        final String flowId,
                        final String executionId,
                        final String triggerId) {
            super(tenantId, namespace, flowId, executionId);
            this.triggerId = Objects.requireNonNull(triggerId, "triggerId cannot be null");
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public URI getContextStorageURI() {
            try {
                String prefix = String.format(PREFIX_FORMAT_TRIGGER,
                    getNamespaceAsPath(),
                    Slugify.of(getFlowId()),
                    getExecutionId(),
                    Slugify.of(getTriggerId())
                );
                return new URI("//" + prefix);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public String toString() {
            return "StorageContext::Trigger";
        }
    }

    /**
     * A storage context scoped to a Trigger.
     */
    @Getter
    public static class Input extends StorageContext {

        private final String inputName;
        private final String fileName;

        private Input(final String tenantId,
                      final String namespace,
                      final String flowId,
                      final String executionId,
                      final String inputName,
                      final String fileName) {
            super(tenantId, namespace, flowId, executionId);
            this.inputName = Objects.requireNonNull(inputName, "inputName cannot be null");
            this.fileName = Objects.requireNonNull(fileName, "fileName cannot be null");
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public URI getContextStorageURI() {
            try {
                var prefix = String.format(
                    PREFIX_FORMAT_INPUTS,
                    getNamespaceAsPath(),
                    Slugify.of(getFlowId()),
                    getExecutionId(),
                    inputName,
                    fileName
                );
                return new URI("//" + prefix);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public String toString() {
            return "StorageContext::Input";
        }
    }
}
