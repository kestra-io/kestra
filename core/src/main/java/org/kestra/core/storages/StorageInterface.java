package org.kestra.core.storages;

import io.micronaut.core.annotation.Introspected;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.utils.Slugify;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

@Introspected
public interface StorageInterface {
    InputStream get(URI uri) throws FileNotFoundException;

    URI put(URI uri, InputStream data) throws IOException;

    boolean delete(URI uri) throws IOException;

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

    default URI uri(Flow flow, Execution execution, String inputName, String file) throws  URISyntaxException {
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

    default URI from(Flow flow, Execution execution, Input input, File file) throws IOException {
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
