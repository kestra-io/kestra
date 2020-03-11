package org.kestra.core.storages;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.utils.Slugify;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

@Introspected
public interface StorageInterface {
    InputStream get(URI uri) throws FileNotFoundException;

    URI put(URI uri, InputStream data) throws IOException;

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

    default URI from(Flow flow, Execution execution, String input, CompletedFileUpload file) throws IOException {
        try {
            //@FIXME: ugly hack to access fileUpload
            try {
                Field f = file.getClass().getDeclaredField("fileUpload");
                f.setAccessible(true);
                FileUpload fileUpload = (FileUpload) f.get(file);

                if (fileUpload instanceof DiskFileUpload) {
                    URI put = this.put(
                        this.uri(flow, execution, input, file.getName()),
                        new BufferedInputStream(new FileInputStream(fileUpload.getFile()))
                    );

                    fileUpload.delete();

                    return put;
                }
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }

            return this.put(
                this.uri(flow, execution, input, file.getName()),
                file.getInputStream()
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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

    static URI outputPrefix(Flow flow, ResolvedTask resolvedTask, Execution execution, TaskRun taskRun)  {
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
