package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
@Property(name="kestra.server-type", value="WORKER")
class ReadFileFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Test
    void readNamespaceFile() throws IllegalVariableEvaluationException, IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(null, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(null, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("Hello from {{ flow.namespace }}".getBytes()));

        String render = variableRenderer.render("{{ render(read('" + filePath + "')) }}", Map.of("flow", Map.of("namespace", namespace)));
        assertThat(render, is("Hello from " + namespace));
    }

    @Test
    void readUnknownNamespaceFile() {
        IllegalVariableEvaluationException illegalVariableEvaluationException = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ read('unknown.txt') }}", Map.of("flow", Map.of("namespace", "io.kestra.tests"))));
        assertThat(illegalVariableEvaluationException.getCause().getCause().getClass(), is(FileNotFoundException.class));
    }

    @Test
    void readInternalStorageFile() throws IOException, IllegalVariableEvaluationException {
        // task output URI format: 'kestra:///$namespace/$flowId/executions/$executionId/tasks/$taskName/$taskRunId/$random.ion'
        String namespace = "my.namespace";
        String flowId = "flow";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/" + flowId + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
        URI internalStorageFile = storageInterface.put(null, internalStorageURI, new ByteArrayInputStream("Hello from a task output".getBytes()));

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace),
            "execution", Map.of("id", executionId)
        );

        String render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render, is("Hello from a task output"));

        // test for an authorized parent execution (execution trigger)
        variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", flowId,
                "namespace", namespace,
                "executionId", executionId
            )
        );

        render = variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables);
        assertThat(render, is("Hello from a task output"));
    }

    @Test
    void readUnauthorizedInternalStorageFile() throws IOException {
        String namespace = "my.namespace";
        String flowId = "flow";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/" + flowId + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
        URI internalStorageFile = storageInterface.put(null, internalStorageURI, new ByteArrayInputStream("Hello from a task output".getBytes()));

        // test for an un-authorized execution with no trigger
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme"),
            "execution", Map.of("id", "notme")
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ read('" + internalStorageFile + "') }}", variables));
        assertThat(exception.getMessage(), is("Unable to read the file '" + internalStorageFile + "' as it didn't belong to the current execution"));

        // test for an un-authorized execution with a trigger of type execution
        Map<String, Object> executionTriggerVariables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme"),
            "execution", Map.of("id", "notme"),
            "trigger", Map.of(
                "flowId", "notme",
                "namespace", "notme",
                "executionId", "notme"
            )
        );

        exception = assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ read('" + internalStorageFile + "') }}", executionTriggerVariables));
        assertThat(exception.getMessage(), is("Unable to read the file '" + internalStorageFile + "' as it didn't belong to the current execution"));

        // test for an un-authorized execution with a trigger of another type
        Map<String, Object> triggerVariables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme"),
            "execution", Map.of("id", "notme"),
            "trigger", Map.of(
                "date", "somedate",
                "row", "somerow"
            )
        );

        exception = assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ read('" + internalStorageFile + "') }}", triggerVariables));
        assertThat(exception.getMessage(), is("Unable to read the file '" + internalStorageFile + "' as it didn't belong to the current execution"));
    }

    @Test
    @Property(name="kestra.server-type", value="EXECUTOR")
    @Disabled("Moved on the next release")
    void readFailOnNonWorkerNodes() {
        IllegalVariableEvaluationException exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ read('unknown.txt') }}", Map.of("flow", Map.of("namespace", "io.kestra.tests"))));
        assertThat(exception.getMessage(), containsString("The 'read' function can only be used in the Worker as it access the internal storage."));
    }
}
