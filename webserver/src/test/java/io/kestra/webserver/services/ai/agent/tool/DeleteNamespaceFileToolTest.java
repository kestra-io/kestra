package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
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
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.put;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.randomNamespace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class DeleteNamespaceFileToolTest {
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private DeleteNamespaceFileTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeMutateConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.MUTATE);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeleteTheDirectoryWithItsContentWhenPathIsADirectory() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/dir/a.txt", "a");
        put(storage, "/dir/sub/b.txt", "b");
        put(storage, "/keep.txt", "keep");

        Map<String, Object> result = ToolCalls.call(tool, "delete_namespace_file", """
            { "namespace": "%s", "path": "/dir" }""".formatted(namespace), MAIN_TENANT);

        List<Map<String, Object>> deleted = (List<Map<String, Object>>) result.get("deleted");
        assertThat(deleted).extracting(entry -> entry.get("path")).containsExactlyInAnyOrder("/dir/", "/dir/a.txt", "/dir/sub/", "/dir/sub/b.txt");
        assertThat(exists(storage, "/dir/sub/b.txt")).isFalse();
        assertThat(exists(storage, "/dir/")).isFalse();
        assertThat(exists(storage, "/keep.txt")).isTrue();
    }

    @Test
    void shouldKeepTheParentDirectoryWhenDeletingItsOnlyFile() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/dir/only.txt", "only");

        DeleteNamespaceFileTool.Result result = tool.deleteNamespaceFile(namespace, "/dir/only.txt", CONTEXT);

        assertThat(result.deleted()).extracting(DeleteNamespaceFileTool.Deleted::path).containsExactly("/dir/only.txt");
        assertThat(exists(storage, "/dir/")).isTrue();
    }

    @Test
    void shouldThrowWhenPathDoesNotExist() {
        String namespace = randomNamespace();

        assertThatThrownBy(() -> tool.deleteNamespaceFile(namespace, "/missing.txt", CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/missing.txt")
            .hasMessageContaining("was not found");
    }
}
