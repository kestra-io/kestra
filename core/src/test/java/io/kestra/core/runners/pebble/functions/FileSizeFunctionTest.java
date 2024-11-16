package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class FileSizeFunctionTest {

    private static final String NAMESPACE = "my.namespace";
    private static final String FLOW = "flow";
    private static final String FILE_TEXT = "Hello from a task output";
    private static final String FILE_SIZE = "24";

    @Inject
    StorageInterface storageInterface;

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void returnsCorrectSize_givenStringUri_andCurrentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE),
            "execution", Map.of("id", executionId)
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size, is(FILE_SIZE));
    }

    @Test
    void returnsCorrectSize_givenStringUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", NAMESPACE,
                "executionId", executionId
            )
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size, is(FILE_SIZE));
    }

    @Test
    void shouldThrowIllegalArgumentException_givenMissingTrigger_andParentExecution() throws IOException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE),
            "execution", Map.of("id", IdUtils.create())
        );

        Exception ex = assertThrows(
            IllegalArgumentException.class,
            () -> variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables)
        );

        assertTrue(ex.getMessage().startsWith("Unable to read the file"), "Exception message doesn't match expected one");
    }

    @Test
    void shouldThrowIllegalArgumentException_givenTrigger_andParentExecution_andMissingNamespace() throws IOException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "executionId", executionId
            )
        );

        Exception ex = assertThrows(
            IllegalArgumentException.class,
            () -> variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables)
        );

        assertTrue(ex.getMessage().startsWith("Unable to read the file"), "Exception message doesn't match expected one");
    }

    @Test
    void returnsCorrectSize_givenUri_andCurrentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE),
            "execution", Map.of("id", executionId),
            "file", internalStorageFile
        );

        String size = variableRenderer.render("{{ fileSize(file) }}", variables);
        assertThat(size, is(FILE_SIZE));
    }

    @Test
    void returnsCorrectSize_givenUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", NAMESPACE,
                "executionId", executionId
            ),
            "file", internalStorageFile
        );

        String size = variableRenderer.render("{{ fileSize(file) }}", variables);
        assertThat(size, is(FILE_SIZE));
    }

    private URI getInternalStorageURI(String executionId) {
        return URI.create("/" + NAMESPACE.replace(".", "/") + "/" + FLOW + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
    }

    private URI getInternalStorageFile(URI internalStorageURI) throws IOException {
        return storageInterface.put(null, internalStorageURI, new ByteArrayInputStream(FILE_TEXT.getBytes()));
    }
}
