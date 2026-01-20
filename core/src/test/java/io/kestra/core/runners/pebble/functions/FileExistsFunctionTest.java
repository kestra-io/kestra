package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
@MicronautTest(rebuildContext = true)
class FileExistsFunctionTest {

    private static final String NAMESPACE = "my.namespace";
    private static final String FLOW = "flow";

    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFactory namespaceFactory;

    private URI getInternalStorageURI(String executionId) {
        return URI.create("/" + NAMESPACE.replace(".", "/") + "/" + FLOW + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
    }

    private URI getInternalStorageFile(URI internalStorageURI, String text) throws IOException {
        return storageInterface.put(MAIN_TENANT, NAMESPACE, internalStorageURI, new ByteArrayInputStream(text.getBytes()));
    }

    @Test
    void shouldReturnTrueForExistingFile() throws IOException, IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI, "EXISTING FILE");

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );
        boolean render = Boolean.parseBoolean(variableRenderer.render("{{ fileExists('" + internalStorageFile + "') }}", variables));
        assertTrue(render);
    }

    @Test
    void readNamespaceFileWithNamespace() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        createNsFile(namespace, false, "NOT AN EMPTY FILE");

        boolean render = Boolean.parseBoolean(
            variableRenderer.render("{{ fileExists('" + filePath + "', namespace='" + namespace + "') }}",
                Map.of("flow", Map.of("namespace", "flow.namespace", "tenantId", MAIN_TENANT))));
        assertTrue(render);
    }

    @Test
    void shouldReturnFalseForNonExistentFile() throws IllegalVariableEvaluationException {
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(executionId);
        URI internalStorageFile = URI.create("kestra://" + internalStorageURI.getRawPath()); // Don't create file just pass the URI.

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", NAMESPACE,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );
        boolean render = Boolean.parseBoolean(variableRenderer.render("{{ fileExists('" + internalStorageFile + "') }}", variables));
        assertFalse(render);
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

        assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ fileExists('unsupported://path-to/file.txt') }}", variables));
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

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ fileExists(file) }}", variables));
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

        assertThat(variableRenderer.render("{{ fileExists(file) }}", variables)).isEqualTo("true");
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

        assertThrows(SecurityException.class, () -> variableRenderer.render("{{ fileExists(file) }}", variables));
    }

    @Test
    void shouldProcessNamespaceFile() throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI file = createNsFile(namespace, false, "Hello World");
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ fileExists(nsfile) }}", variables)).isEqualTo("true");
    }

    @Test
    void shouldProcessNamespaceFileFromAnotherNamespace() throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI file = createNsFile(namespace, true, "Hello World");
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", TestsUtils.randomNamespace(),
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ fileExists(nsfile) }}", variables)).isEqualTo("true");
    }

    private URI createNsFile(String namespace, boolean nsInAuthority, String value) throws IOException, URISyntaxException {
        String filePath = "file.txt";
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream(value.getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("%s-file".formatted(IdUtils.create()), ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }
}
