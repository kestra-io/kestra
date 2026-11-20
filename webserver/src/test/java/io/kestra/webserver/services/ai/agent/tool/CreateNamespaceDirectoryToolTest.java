package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.exists;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.randomNamespace;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CreateNamespaceDirectoryToolTest {
    @Inject
    private CreateNamespaceDirectoryTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeMutateConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.MUTATE);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    void shouldCreateTheDirectoryAndItsParentsWhenInvoked() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);

        Map<String, Object> result = ToolCalls.call(tool, "create_namespace_directory", """
            { "namespace": "%s", "path": "scripts/lib" }""".formatted(namespace), MAIN_TENANT);

        assertThat(result).containsEntry("path", "/scripts/lib/");
        assertThat(exists(storage, "/scripts/lib/")).isTrue();
        assertThat(exists(storage, "/scripts/")).isTrue();
    }
}
