package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.exists;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.randomNamespace;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class WriteNamespaceFileToolTest {
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private WriteNamespaceFileTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeMutateConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.MUTATE);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    void shouldCreateTheFileAndItsParentDirectoriesWhenPathIsNew() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);

        Map<String, Object> result = ToolCalls.call(tool, "write_namespace_file", """
            { "namespace": "%s", "path": "/scripts/lib/etl.py", "content": "print('hé')" }""".formatted(namespace), MAIN_TENANT);

        assertThat(result).containsEntry("path", "/scripts/lib/etl.py").containsEntry("revision", 1).containsEntry("size", 12);
        assertThat(read(storage, "/scripts/lib/etl.py")).isEqualTo("print('hé')");
        assertThat(exists(storage, "/scripts/lib/")).isTrue();
    }

    @Test
    void shouldRefuseToReplaceAnExistingFileUnlessOverwriteIsSet() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        tool.writeNamespaceFile(namespace, "/config.yml", "a: 1", null, CONTEXT);

        assertThatThrownBy(() -> tool.writeNamespaceFile(namespace, "/config.yml", "a: 2", false, CONTEXT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists")
            .hasMessageContaining("overwrite=true");
        assertThat(read(storage, "/config.yml")).isEqualTo("a: 1");

        WriteNamespaceFileTool.Result replaced = tool.writeNamespaceFile(namespace, "/config.yml", "a: 2", true, CONTEXT);

        assertThat(replaced.revision()).isEqualTo(2);
        assertThat(read(storage, "/config.yml")).isEqualTo("a: 2");
    }

    @Test
    void shouldRefuseThePathWhenItIsInsideTheReservedFlowsFolder() {
        String namespace = randomNamespace();

        assertThatThrownBy(() -> tool.writeNamespaceFile(namespace, "/_flows/hello.yml", "id: hello", null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved for flows");
        assertThat(exists(namespaceFactory.of(MAIN_TENANT, namespace), "/_flows/hello.yml")).isFalse();
    }
}
