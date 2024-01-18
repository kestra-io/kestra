package io.kestra.core.storages;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import io.kestra.core.annotations.Retryable;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.utils.Slugify;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Introspected
public interface StorageInterface {
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    InputStream get(String tenantId, URI uri) throws IOException;

    /**
     * Returns all objects that start with the given prefix
     * @param includeDirectories whether to include directories in the given results or not. If true, directories' uri will have a trailing '/'
     * @return Kestra's internal storage uris of the found objects
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    List<URI> allByPrefix(String tenantId, URI prefix, boolean includeDirectories) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    List<FileAttributes> list(String tenantId, URI uri) throws IOException;


    /**
     * Whether the uri points to a file/object that exist in the internal storage.
     *
     * @param uri      the URI of the file/object in the internal storage.
     * @param tenantId the tenant identifier.
     * @return true if the uri points to a file/object that exist in the internal storage.
     */
    @SuppressWarnings("try")
    default boolean exists(String tenantId, URI uri) {
        try (InputStream ignored = get(tenantId, uri)){
            return true;
        } catch (IOException ieo) {
            return false;
        }
    }

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    FileAttributes getAttributes(String tenantId, URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    URI put(String tenantId, URI uri, InputStream data) throws IOException;

    @Retryable(includes = {IOException.class})
    boolean delete(String tenantId, URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    URI createDirectory(String tenantId, URI uri) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    URI move(String tenantId, URI from, URI to) throws IOException;

    @Retryable(includes = {IOException.class})
    List<URI> deleteByPrefix(String tenantId, URI storagePrefix) throws IOException;

    default String executionPrefix(Flow flow, Execution execution) {
        return fromParts(
            flow.getNamespace().replace(".", "/"),
            Slugify.of(flow.getId()),
            "executions",
            execution.getId()
        );
    }

    default String executionPrefix(Execution execution) {
        return fromParts(
            execution.getNamespace().replace(".", "/"),
            Slugify.of(execution.getFlowId()),
            "executions",
            execution.getId()
        );
    }

    default String executionPrefix(TaskRun taskRun) {
        return fromParts(
            taskRun.getNamespace().replace(".", "/"),
            Slugify.of(taskRun.getFlowId()),
            "executions",
            taskRun.getExecutionId()
        );
    }

    default String statePrefix(String namespace, @Nullable String flowId, @Nullable String name, @Nullable String value) {
        String namespacePrefix = namespace.replace(".", "/");

        ArrayList<String> paths = new ArrayList<>(
            List.of(
                namespacePrefix
            )
        );

        if (flowId != null) {
            paths.add(Slugify.of(flowId));
        }

        paths.add("states");

        if (name != null) {
            paths.add(name);
        }

        if (value != null) {
            paths.add(Hashing
                .goodFastHash(64)
                .hashString(value, Charsets.UTF_8)
                .toString()
            );
        }

        return String.join("/", paths);
    }

    default String cachePrefix(String namespace, String flowId, String taskId, @Nullable String value) {
        String namespacePrefix = namespace.replace(".", "/");

        ArrayList<String> paths = new ArrayList<>(
            List.of(
                namespacePrefix,
                Slugify.of(flowId),
                Slugify.of(taskId),
                "cache"
            )
        );

        if (value != null) {
            paths.add(Hashing
                .goodFastHash(64)
                .hashString(value, Charsets.UTF_8)
                .toString()
            );
        }

        return String.join("/", paths);
    }

    default String namespaceFilePrefix(String namespace) {
        return fromParts(
            namespace.replace(".", "/"),
            "_files"
        );
    }

    default Optional<String> extractExecutionId(URI path) {
        Pattern pattern = Pattern.compile("^/(.+)/executions/([^/]+)/", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(path.getPath());

        if (!matcher.find() || matcher.group(2).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(2));
    }

    default URI uri(Flow flow, Execution execution, String inputName, String file) throws URISyntaxException {
        return new URI("/" + fromParts(
            executionPrefix(flow, execution),
            "inputs",
            inputName,
            file
        ));
    }

    @Retryable(includes = {IOException.class})
    default URI from(Flow flow, Execution execution, String input, File file) throws IOException {
        try {
            return this.put(
                flow.getTenantId(),
                this.uri(flow, execution, input, file.getName()),
                new BufferedInputStream(new FileInputStream(file))
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Retryable(includes = {IOException.class})
    default URI from(Flow flow, Execution execution, Input<?> input, File file) throws IOException {
        return this.from(flow, execution, input.getName(), file);
    }

    default URI outputPrefix(Flow flow) {
        try {
            return new URI("//" + fromParts(
                flow.getNamespace().replace(".", "/"),
                Slugify.of(flow.getId())
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    default URI outputPrefix(Flow flow, Task task, Execution execution, TaskRun taskRun) {
        try {
            return new URI("//" + fromParts(
                flow.getNamespace().replace(".", "/"),
                Slugify.of(flow.getId()),
                "executions",
                execution.getId(),
                "tasks",
                Slugify.of(taskRun.getTaskId()),
                taskRun.getId()
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    default URI outputPrefix(TriggerContext triggerContext, AbstractTrigger trigger, String triggerExecutionId) {
        try {
            return new URI("//" + fromParts(
                triggerContext.getNamespace().replace(".", "/"),
                Slugify.of(triggerContext.getFlowId()),
                "executions",
                triggerExecutionId,
                "trigger",
                Slugify.of(trigger.getId())
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String fromParts(String... parts) {
        return "/" + Arrays.stream(parts)
            .filter(part -> part != null)
            .collect(Collectors.joining("/"));
    }
}
