package io.kestra.core.storages;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import io.kestra.core.annotations.Retryable;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.Task;
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

@Introspected
public interface StorageInterface {
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    InputStream get(URI uri) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    Long size(URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    URI put(URI uri, InputStream data) throws IOException;

    @Retryable(includes = {IOException.class})
    boolean delete(URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    List<URI> deleteByPrefix(URI storagePrefix) throws IOException;

    default String executionPrefix(Flow flow, Execution execution) {
        return String.join(
            "/",
            Arrays.asList(
                flow.getNamespace().replace(".", "/"),
                Slugify.of(flow.getId()),
                "executions",
                execution.getId()
            )
        );
    }

    default String executionPrefix(Execution execution) {
        return String.join(
            "/",
            Arrays.asList(
                execution.getNamespace().replace(".", "/"),
                Slugify.of(execution.getFlowId()),
                "executions",
                execution.getId()
            )
        );
    }

    default String executionPrefix(TaskRun taskRun) {
        return String.join(
            "/",
            Arrays.asList(
                taskRun.getNamespace().replace(".", "/"),
                Slugify.of(taskRun.getFlowId()),
                "executions",
                taskRun.getExecutionId()
            )
        );
    }

    default String statePrefix(String namespace, @Nullable String flowId, @Nullable String name, @Nullable String value) {
        String namespacePrefix = namespace.replace(".", "/");

        ArrayList<String> paths = new ArrayList<>(
            flowId == null ? List.of(
                namespacePrefix,
                "states"
            ) : List.of(
                namespacePrefix,
                Slugify.of(flowId),
                "states"
            )
        );

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

    default Optional<String> extractExecutionId(URI path) {
        Pattern pattern = Pattern.compile("^/(.+)/executions/([^/]+)/", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(path.getPath());

        if (!matcher.find() || matcher.group(2).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(2));
    }

    default URI uri(Flow flow, Execution execution, String inputName, String file) throws URISyntaxException {
        return new URI("/" + String.join(
            "/",
            Arrays.asList(
                executionPrefix(flow, execution),
                "inputs",
                inputName,
                file
            )
        ));
    }

    @Retryable(includes = {IOException.class})
    default URI from(Flow flow, Execution execution, String input, File file) throws IOException {
        try {
            return this.put(
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

    default URI outputPrefix(Flow flow)  {
        try {
            return new URI("/" + String.join(
                "/",
                Arrays.asList(
                    flow.getNamespace().replace(".", "/"),
                    Slugify.of(flow.getId())
                )
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    default URI outputPrefix(Flow flow, Task task, Execution execution, TaskRun taskRun)  {
        try {
            return new URI("/" + String.join(
                "/",
                Arrays.asList(
                    flow.getNamespace().replace(".", "/"),
                    Slugify.of(flow.getId()),
                    "executions",
                    execution.getId(),
                    "tasks",
                    Slugify.of(taskRun.getTaskId()),
                    taskRun.getId()
                )
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
