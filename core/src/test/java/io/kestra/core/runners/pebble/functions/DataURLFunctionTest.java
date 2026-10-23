package io.kestra.core.runners.pebble.functions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

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
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariablesWithExecution;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
@Property(name = "kestra.server-type", value = "STANDALONE")
@Execution(ExecutionMode.SAME_THREAD)
class DataURLFunctionTest {
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";

    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFactory namespaceFactory;

    @Test
    void shouldResolveNamespaceFileToDataUrl() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI nsFile = upsertNsFile(false, namespace, SVG);

        String render = variableRenderer.render("{{ dataURL('" + nsFile + "') }}", getVariables(namespace));

        assertThat(render).startsWith("data:image/svg+xml;base64,");
        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldResolveNamespaceFileFromAnotherNamespaceToDataUrl() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        URI nsFile = upsertNsFile(true, "io.kestra.tests", SVG);

        String render = variableRenderer.render("{{ dataURL('" + nsFile + "') }}", getVariables("notme"));

        assertThat(render).startsWith("data:image/svg+xml;base64,");
        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldResolveInternalStorageFileToDataUrl() throws IOException, IllegalVariableEvaluationException {
        String namespace = "my.namespace";
        String executionId = IdUtils.create();
        URI internalStorageURI = URI.create("/" + namespace.replace(".", "/") + "/flow/executions/" + executionId + "/tasks/task/" + IdUtils.create() + "/123456.svg");
        URI internalStorageFile = storageInterface.put(MAIN_TENANT, namespace, internalStorageURI, new ByteArrayInputStream(SVG.getBytes()));

        Map<String, Object> variables = getVariablesWithExecution(namespace, executionId);

        String render = variableRenderer.render("{{ dataURL('" + internalStorageFile + "') }}", variables);

        assertThat(render).startsWith("data:image/svg+xml;base64,");
        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldFailForUnrecognizedExtension() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        URI nsFile = upsertNsFile("file.unknown", false, namespace, SVG);

        IllegalVariableEvaluationException exception = assertThrows(
            IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ dataURL('" + nsFile + "') }}", getVariables(namespace))
        );
        assertThat(exception.getCause().getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getCause().getMessage()).contains("unrecognized file extension");
    }

    @Test
    void shouldResolveSpecificRevisionToDataUrl() throws IOException, URISyntaxException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        URI nsFile = upsertNsFile(false, namespace, SVG);
        String svgV2 = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>";
        namespaceFactory.of(MAIN_TENANT, namespace).putFile(Path.of(nsFile.getPath()), new ByteArrayInputStream(svgV2.getBytes()));

        String render = variableRenderer.render("{{ dataURL('" + nsFile + "', revision=1) }}", getVariables(namespace));

        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldResolveUnrecognizedExtensionToDataUrlGivenExplicitMimeType() throws IOException, URISyntaxException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        URI nsFile = upsertNsFile("file.unknown", false, namespace, SVG);

        String render = variableRenderer.render("{{ dataURL('" + nsFile + "', mimeType='image/svg+xml') }}", getVariables(namespace));

        assertThat(render).startsWith("data:image/svg+xml;base64,");
        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldResolveToBase64ContentOnlyGivenContentOnlyTrue() throws IOException, URISyntaxException, IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        URI nsFile = upsertNsFile(false, namespace, SVG);

        String render = variableRenderer.render("{{ dataURL('" + nsFile + "', contentOnly=true) }}", getVariables(namespace));

        assertThat(render).doesNotContain("data:", ";base64,");
        assertThat(new String(Base64.getDecoder().decode(render))).isEqualTo(SVG);
    }

    @Test
    void shouldFailForUnsupportedScheme() {
        Map<String, Object> variables = getVariablesWithExecution("notme", "notme");

        var exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ dataURL('unsupported://path-to/file.svg') }}", variables));
        assertThat(exception.getCause()).isInstanceOf(PebbleException.class);
        assertThat(exception.getCause().getMessage()).contains("Cannot process the URI unsupported://path-to/file.svg: scheme not supported.");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void shouldResolveLocalFileToDataUrlWhenAllowed() throws IOException, IllegalVariableEvaluationException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT
            ),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        String render = variableRenderer.render("{{ dataURL(file) }}", variables);

        assertThat(render).startsWith("data:image/svg+xml;base64,");
        assertThat(decode(render)).isEqualTo(SVG);
    }

    @Test
    void shouldFailForLocalFileWhenNotAllowed() throws IOException {
        URI file = createFile();
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "notme",
                "namespace", "notme",
                "tenantId", MAIN_TENANT
            ),
            "execution", Map.of("id", "notme"),
            "file", file.toString()
        );

        var exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ dataURL(file) }}", variables));
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    private static String decode(String dataUrl) {
        String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
        return new String(Base64.getDecoder().decode(base64));
    }

    private static URI createFile() throws IOException {
        File tempFile = Files.createTempFile(Path.of("/tmp"), "file", ".svg").toFile();
        Files.write(tempFile.toPath(), SVG.getBytes());
        return tempFile.toPath().toUri();
    }

    private URI upsertNsFile(boolean nsInAuthority, String namespace, String value) throws IOException, URISyntaxException {
        return upsertNsFile(IdUtils.create() + "file.svg", nsInAuthority, namespace, value);
    }

    private URI upsertNsFile(String filePath, boolean nsInAuthority, String namespace, String value) throws IOException, URISyntaxException {
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream(value.getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
