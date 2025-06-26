package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(rebuildContext = true)
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
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void readNamespaceFileWithNamespace() throws IllegalVariableEvaluationException, IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream(FILE_TEXT.getBytes()));

        String render = variableRenderer.render("{{ fileSize('" + filePath + "', namespace='" + namespace + "') }}", Map.of("flow", Map.of("namespace", "flow.namespace", "tenantId", MAIN_TENANT)));
        assertThat(render).isEqualTo(FILE_SIZE);
    }

    @Test
    void returnsCorrectSize_givenStringUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", NAMESPACE,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
            )
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void shouldReadFromAnotherExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create())
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void shouldThrowIllegalArgumentException_givenTrigger_andParentExecution_andMissingNamespace() throws IOException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
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
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId),
            "file", internalStorageFile
        );

        String size = variableRenderer.render("{{ fileSize(file) }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void returnsCorrectSize_givenUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", NAMESPACE,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
            ),
            "file", internalStorageFile
        );

        String size = variableRenderer.render("{{ fileSize(file) }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void shouldFailProcessingUnsupportedScheme() {
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme")
        );

        assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ fileSize('unsupported://path-to/file.txt') }}", variables));
    }

    @Test
    void shouldFailProcessingNotAllowedPath() throws IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ fileSize(file) }}", variables));
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void shouldSucceedProcessingAllowedFile() throws IllegalVariableEvaluationException, IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThat(variableRenderer.render("{{ fileSize(file) }}", variables)).isEqualTo("11");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    @Property(name = LocalPath.ENABLE_FILE_FUNCTIONS_CONFIG, value = "false")
    void shouldFailProcessingAllowedFileIfFileFunctionDisabled() throws IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ fileSize(file) }}", variables));
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI getInternalStorageURI(String executionId) {
        return URI.create("/" + NAMESPACE.replace(".", "/") + "/" + FLOW + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
    }

    private URI getInternalStorageFile(URI internalStorageURI) throws IOException {
        return storageInterface.put(MAIN_TENANT, null, internalStorageURI, new ByteArrayInputStream(FILE_TEXT.getBytes()));
    }
}
