package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.put;
import static io.kestra.webserver.services.ai.agent.tool.NamespaceFileFixtures.randomNamespace;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ListNamespaceFilesToolTest {
    @Inject
    private ListNamespaceFilesTool tool;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldListOnlyTheDirectChildrenWhenFilteringOnParentPath() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        put(storage, "/root.txt", "root");
        put(storage, "/dir/child.txt", "child");
        put(storage, "/dir/sub/grandchild.txt", "grandchild");

        Map<String, Object> result = ToolCalls.call(
            tool, "list_namespace_files", """
                { "namespace": "%s", "PARENT_PATH": { "operator": "EQUALS", "value": "/dir/" } }""".formatted(namespace), MAIN_TENANT
        );

        assertThat(paths(result)).containsExactlyInAnyOrder("/dir/child.txt", "/dir/sub/");
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(2);
    }

    @Test
    void shouldListTheNamespaceOfTheParameterWhenTheModelAlsoSendsANamespaceFilter() {
        String namespace = randomNamespace();
        put(namespaceFactory.of(MAIN_TENANT, namespace), "/mine.txt", "mine");

        Map<String, Object> result = ToolCalls.call(
            tool, "list_namespace_files", """
                { "namespace": "%s", "NAMESPACE": { "operator": "EQUALS", "value": "io.kestra.test.ai.other" } }""".formatted(namespace), MAIN_TENANT
        );

        assertThat(paths(result)).containsExactly("/mine.txt");
    }

    @Test
    void shouldPageAndReportTheTotalWhenMoreEntriesThanOnePageHolds() {
        String namespace = randomNamespace();
        Namespace storage = namespaceFactory.of(MAIN_TENANT, namespace);
        int files = 101;
        for (int i = 0; i < files; i++) {
            put(storage, "/file-%03d.txt".formatted(i), "content");
        }

        Map<String, Object> first = ToolCalls.call(tool, "list_namespace_files", """
            { "namespace": "%s" }""".formatted(namespace), MAIN_TENANT);
        Map<String, Object> second = ToolCalls.call(tool, "list_namespace_files", """
            { "namespace": "%s", "page": 2 }""".formatted(namespace), MAIN_TENANT);

        assertThat(paths(first)).hasSize(100);
        assertThat(((Number) first.get("total")).longValue()).isEqualTo(files);
        assertThat(paths(second)).hasSize(1).doesNotContainAnyElementsOf(paths(first));
        assertThat(second.get("page")).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private static List<String> paths(final Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("files")).stream().map(entry -> (String) entry.get("path")).toList();
    }
}
