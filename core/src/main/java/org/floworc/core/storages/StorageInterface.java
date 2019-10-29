package org.floworc.core.storages;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.Input;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.utils.Slugify;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public interface StorageInterface {
    InputStream get(URI uri) throws FileNotFoundException;

    StorageObject put(URI uri, InputStream data) throws IOException;

    default URI uri(Flow flow, Execution execution, String inputName, String file) throws  URISyntaxException {
        return new URI("/" + String.join(
            "/",
            Arrays.asList(
                flow.getNamespace().replace(".", "/"),
                Slugify.of(flow.getId()),
                "executions",
                execution.getId(),
                "inputs",
                inputName,
                file
            )
        ));
    }

    default StorageObject from(Flow flow, Execution execution, String input, File file) throws IOException {
        try {
            return this.put(
                this.uri(flow, execution, input, file.getName()),
                new FileInputStream(file)
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    default StorageObject from(Flow flow, Execution execution, Input input, File file) throws IOException {
        return this.from(flow, execution, input.getName(), file);
    }

    static URI outputPrefix(Flow flow, Task task, Execution execution, TaskRun taskRun)  {
        try {
            return new URI(String.join(
                "/",
                Arrays.asList(
                    flow.getNamespace().replace(".", "/"),
                    Slugify.of(flow.getId()),
                    "executions",
                    execution.getId(),
                    "tasks",
                    Slugify.of(task.getId()),
                    taskRun.getId()
                )
            ));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
