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
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class MoveNamespaceFileToolTest {
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private MoveNamespaceFileTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeMutateConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.MUTATE);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMoveTheDirectoryWithItsContentWhenSourceIsADirectory() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/src/a.txt", "a");
        put(storage, "/src/sub/b.txt", "b");

        Map<String, Object> result = ToolCalls.call(tool, "move_namespace_file", """
            { "namespace": "%s", "from": "/src", "to": "/dst" }""".formatted(namespace), MAIN_TENANT);

        List<Map<String, Object>> moved = (List<Map<String, Object>>) result.get("moved");
        assertThat(moved).extracting(entry -> entry.get("to")).containsExactlyInAnyOrder("/dst/", "/dst/a.txt", "/dst/sub/", "/dst/sub/b.txt");
        assertThat(moved).filteredOn(entry -> "/dst/a.txt".equals(entry.get("to"))).singleElement().satisfies(entry -> assertThat(entry).containsEntry("from", "/src/a.txt"));
        assertThat(read(storage, "/dst/sub/b.txt")).isEqualTo("b");
        assertThat(exists(storage, "/src/a.txt")).isFalse();
        assertThat(exists(storage, "/src/")).isFalse();
    }

    @Test
    void shouldMoveAFileRewrittenAtAPathThatWasMovedAwayBefore() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/a.txt", "first");
        tool.moveNamespaceFile(namespace, "/a.txt", "/b.txt", CONTEXT);
        put(storage, "/a.txt", "second");

        MoveNamespaceFileTool.Result result = tool.moveNamespaceFile(namespace, "/a.txt", "/c.txt", CONTEXT);

        assertThat(result.moved()).extracting(MoveNamespaceFileTool.Moved::to).containsExactly("/c.txt");
        assertThat(read(storage, "/c.txt")).isEqualTo("second");
        assertThat(read(storage, "/b.txt")).isEqualTo("first");
    }

    @Test
    void shouldThrowWhenSourceDoesNotExist() {
        String namespace = randomNamespace();

        assertThatThrownBy(() -> tool.moveNamespaceFile(namespace, "/missing.txt", "/moved.txt", CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/missing.txt")
            .hasMessageContaining("was not found");
    }

    @Test
    void shouldThrowAndKeepBothFilesWhenDestinationExists() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/a.txt", "a");
        put(storage, "/b.txt", "b");

        assertThatThrownBy(() -> tool.moveNamespaceFile(namespace, "/a.txt", "/b.txt", CONTEXT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/b.txt")
            .hasMessageContaining("already exists");
        assertThat(read(storage, "/a.txt")).isEqualTo("a");
        assertThat(read(storage, "/b.txt")).isEqualTo("b");
    }
}
