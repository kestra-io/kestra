package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class FileURIFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    NamespaceFactory namespaceFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void fileURIFunction() throws IllegalVariableEvaluationException{
        String namespace = "my.namespace";
        String flowId = "flow";

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "fileA", "test"
        );
        String render = variableRenderer.render("{{ fileURI(fileA) }}", variables);
        assertThat(render).isEqualTo("kestra:///my/namespace/_files/test");
    }

    @Test
    void fileURIFunctionShouldThrowForIncorrectPath() throws IllegalVariableEvaluationException{
        String namespace = "my.namespace";
        String flowId = "flow";

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "fileA", "../test"
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ fileURI(fileA) }}", variables));
        assertThat(exception.getMessage()).isEqualTo("Path must not contain '../'");
    }

    @Test
    void fileURIFunctionResolvesLatestVersion() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        String filePath = "my_file.txt";
        
        upsertNsFile(filePath, namespace, "Version 1");
        upsertNsFile(filePath, namespace, "Version 2");
        
        Map<String, Object> variables = getVariables(namespace);
        
        String render = variableRenderer.render("{{ fileURI('" + filePath + "') }}", variables);
        assertThat(render).isEqualTo("kestra:///" + namespace.replace(".", "/") + "/_files/" + filePath + ".v2");
        
        String readContent = variableRenderer.render("{{ read('" + filePath + "') }}", variables);
        assertThat(readContent).isEqualTo("Version 2");
    }

    @Test
    void fileURIFunctionWithExplicitVersion() throws IllegalVariableEvaluationException, IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        String filePath = "my_file.txt";
        
        upsertNsFile(filePath, namespace, "Version 1");
        upsertNsFile(filePath, namespace, "Version 2");
        
        Map<String, Object> variables = getVariables(namespace);
        
        String render = variableRenderer.render("{{ fileURI('" + filePath + "', version=1) }}", variables);
        assertThat(render).isEqualTo("kestra:///" + namespace.replace(".", "/") + "/_files/" + filePath);
        
        String readContent = variableRenderer.render("{{ read('" + filePath + "', version=1) }}", variables);
        assertThat(readContent).isEqualTo("Version 1");
    }

    @Test
    void fileURIFunctionForNonExistentFile() throws IllegalVariableEvaluationException {
        String namespace = TestsUtils.randomNamespace();
        String filePath = "non_existent_file.txt";
        
        Map<String, Object> variables = getVariables(namespace);
        
        String render = variableRenderer.render("{{ fileURI('" + filePath + "') }}", variables);
        assertThat(render).isEqualTo("kestra:///" + namespace.replace(".", "/") + "/_files/" + filePath);
    }

    private void upsertNsFile(String filePath, String namespace, String value) throws IOException, URISyntaxException {
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream(value.getBytes()));
    }

}
