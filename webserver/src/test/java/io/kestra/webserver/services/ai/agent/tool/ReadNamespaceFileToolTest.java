package io.kestra.webserver.services.ai.agent.tool;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
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
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.put;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.randomNamespace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class ReadNamespaceFileToolTest {
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ReadNamespaceFileTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReadTheRequestedRevisionWhenFileWasRewritten() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/script.py", "print('v1')");
        put(storage, "/script.py", "print('v2')");

        Map<String, Object> latest = ToolCalls.call(tool, "read_namespace_file", """
            { "namespace": "%s", "path": "/script.py" }""".formatted(namespace), MAIN_TENANT);
        Map<String, Object> first = ToolCalls.call(tool, "read_namespace_file", """
            { "namespace": "%s", "path": "script.py", "revision": 1 }""".formatted(namespace), MAIN_TENANT);

        assertThat(latest).containsEntry("content", "print('v2')").containsEntry("revision", 2).containsEntry("path", "/script.py");
        assertThat(first).containsEntry("content", "print('v1')").containsEntry("revision", 1);
    }

    @Test
    void shouldPageTheContentWhenLongerThanMaxLength() {
        String namespace = randomNamespace();
        put(namespaceFactory.of(MAIN_TENANT, namespace), "/data.txt", "0123456789");

        ReadNamespaceFileTool.Result head = tool.readNamespaceFile(namespace, "/data.txt", null, null, 4, CONTEXT);
        ReadNamespaceFileTool.Result tail = tool.readNamespaceFile(namespace, "/data.txt", null, 8, 4, CONTEXT);

        assertThat(head.content()).isEqualTo("0123");
        assertThat(head.nextOffset()).isEqualTo(4);
        assertThat(tail.content()).isEqualTo("89");
        assertThat(tail.offset()).isEqualTo(8);
        assertThat(tail.nextOffset()).isNull();
    }

    @Test
    void shouldThrowWhenFileDoesNotExist() {
        String namespace = randomNamespace();

        assertThatThrownBy(() -> tool.readNamespaceFile(namespace, "/missing.txt", null, null, null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/missing.txt")
            .hasMessageContaining("was not found");
    }

    @Test
    void shouldRefuseWhenFileIsNotValidUtf8Text() throws Exception {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        storage.putFile(Path.of("/binary.dat"), new ByteArrayInputStream(new byte[] { (byte) 0xff, (byte) 0xfe, 0, 1 }), Namespace.Conflicts.ERROR);

        assertThatThrownBy(() -> tool.readNamespaceFile(namespace, "/binary.dat", null, null, null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not valid UTF-8");
    }
}
