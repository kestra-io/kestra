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
import static org.junit.jupiter.api.Assertions.*;

@KestraTest(rebuildContext = true)
class IsFileEmptyFunctionTest {

    private static final String NAMESPACE = "my.namespace";
    private static final String FLOW = "flow";

    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    private URI getInternalStorageURI(String executionId) {
        return URI.create("/" + NAMESPACE.replace(".", "/") + "/" + FLOW + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
    }

    private URI getInternalStorageFile(URI internalStorageURI, String text) throws IOException {
        return storageInterface.put(MAIN_TENANT, NAMESPACE, internalStorageURI, new ByteArrayInputStream(text.getBytes()));
    }

    @Test
    void shouldReturnFalseForFileWithText() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI, "NOT AN EMPTY FILE");

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );
        boolean render = Boolean.parseBoolean(variableRenderer.render("{{ isFileEmpty('" + internalStorageFile + "') }}", variables));
        assertFalse(render);
    }

    @Test
    void readNamespaceFileWithNamespace() throws IllegalVariableEvaluationException, IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("NOT AN EMPTY FILE".getBytes()));

        boolean render = Boolean.parseBoolean(
            variableRenderer.render("{{ isFileEmpty('" + filePath + "', namespace='" + namespace + "') }}",
                Map.of("flow", Map.of("namespace", "flow.namespace", "tenantId", MAIN_TENANT))));
        assertFalse(render);
    }

    @Test
    void shouldReturnTrueForEmpty() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI, "");

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );
        boolean render = Boolean.parseBoolean(variableRenderer.render("{{ isFileEmpty('" + internalStorageFile + "') }}", variables));
        assertTrue(render);
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

        assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ isFileEmpty('unsupported://path-to/file.txt') }}", variables));
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

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ isFileEmpty(file) }}", variables));
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

        assertThat(variableRenderer.render("{{ isFileEmpty(file) }}", variables)).isEqualTo("false");
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

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ isFileEmpty(file) }}", variables));
    }


    @Test
    void shouldProcessNamespaceFile() throws IOException, IllegalVariableEvaluationException {
        URI file = createNsFile(false);
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", "io.kestra.tests",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ isFileEmpty(nsfile) }}", variables)).isEqualTo("false");
    }

    @Test
    void shouldProcessNamespaceFileFromAnotherNamespace() throws IOException, IllegalVariableEvaluationException {
        URI file = createNsFile(true);
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ isFileEmpty(nsfile) }}", variables)).isEqualTo("false");
    }

    private URI createNsFile(boolean nsInAuthority) throws IOException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }
}