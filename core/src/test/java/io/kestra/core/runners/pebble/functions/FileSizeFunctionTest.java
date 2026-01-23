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
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
@Execution(ExecutionMode.SAME_THREAD)
public class FileSizeFunctionTest {
    private static final String FLOW = "flow";
    private static final String FILE_TEXT = "Hello from a task output";
    private static final String FILE_SIZE = "24";

    @Inject
    StorageInterface storageInterface;

    @Inject
    VariableRenderer variableRenderer;
    
    @Inject
    NamespaceFactory namespaceFactory;

    @Test
    void returnsCorrectSize_givenStringUri_andCurrentExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(namespace, executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void readNamespaceFileWithNamespace() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI file = createNsFile(namespace, false, FILE_TEXT);

        String render = variableRenderer.render("{{ fileSize('" + file.getPath() + "', namespace='" + namespace + "') }}", Map.of("flow", Map.of("namespace", "flow.namespace", "tenantId", MAIN_TENANT)));
        assertThat(render).isEqualTo(FILE_SIZE);
    }

    @Test
    void returnsCorrectSize_givenStringUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(namespace, executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", namespace,
                "executionId", executionId,
                "tenantId", MAIN_TENANT
            )
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void shouldReadFromAnotherExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(namespace, executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create())
        );

        String size = variableRenderer.render("{{ fileSize('" + internalStorageFile + "') }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void returnsCorrectSize_givenUri_andCurrentExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(namespace, executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        // test for an authorized execution
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", FLOW,
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId),
            "file", internalStorageFile
        );

        String size = variableRenderer.render("{{ fileSize(file) }}", variables);
        assertThat(size).isEqualTo(FILE_SIZE);
    }

    @Test
    void returnsCorrectSize_givenUri_andParentExecution() throws IOException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String executionId = IdUtils.create();
        URI internalStorageURI = getInternalStorageURI(namespace, executionId);
        URI internalStorageFile = getInternalStorageFile(internalStorageURI);

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "subflow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", IdUtils.create()),
            "trigger", Map.of(
                "flowId", FLOW,
                "namespace", namespace,
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

        assertThat(variableRenderer.render("{{ fileSize(nsfile) }}", variables)).isEqualTo("11");
    }

    @Test
    void shouldProcessNamespaceFileFromAnotherNamespace() throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI file = createNsFile(namespace, true, "Hello World");
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", "notme",
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", "execution"),
            "nsfile", file.toString()
        );

        assertThat(variableRenderer.render("{{ fileSize(nsfile) }}", variables)).isEqualTo("11");
    }

    private URI createNsFile(String namespace, boolean nsInAuthority, String value) throws IOException, URISyntaxException {
        String filePath = "%sfile.txt".formatted(IdUtils.create());
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream(value.getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("%sfile".formatted(IdUtils.create()), ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI getInternalStorageURI(String namespace, String executionId) {
        return URI.create("/" + namespace.replace(".", "/") + "/" + FLOW + "/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.ion");
    }

    private URI getInternalStorageFile(URI internalStorageURI) throws IOException {
        return storageInterface.put(MAIN_TENANT, null, internalStorageURI, new ByteArrayInputStream(FILE_TEXT.getBytes()));
    }
}
