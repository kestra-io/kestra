package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(environments = "memory")
class ListNamespacesToolTest {
    @Inject
    private ListNamespacesTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldListNamespaceTreeWithFlowPresenceWhenInvoked() {
        String tenant = newTenant();
        String root = "company" + IdUtils.create().toLowerCase();
        createFlow(tenant, root + ".team.etl");

        Map<String, Object> result = ToolCalls.call(tool, "list_namespaces", "{}", tenant);

        assertThat(namespaces(result))
            .extracting(namespace -> namespace.get("id"), namespace -> namespace.get("hasFlows"))
            .containsExactly(
                tuple(root, false),
                tuple(root + ".team", false),
                tuple(root + ".team.etl", true),
                tuple("system", false)
            );
        assertThat(result).containsEntry("total", 4).containsEntry("page", 1);
    }

    @Test
    void shouldListOnlyNamespacesHoldingFlowsWhenWithFlowsOnly() {
        String tenant = newTenant();
        String root = "company" + IdUtils.create().toLowerCase();
        createFlow(tenant, root + ".team.etl");
        createFlow(tenant, root + ".team.reporting");

        Map<String, Object> result = ToolCalls.call(tool, "list_namespaces", """
            { "withFlowsOnly": true }""", tenant);

        assertThat(namespaces(result)).extracting(namespace -> namespace.get("id"))
            .containsExactly(root + ".team.etl", root + ".team.reporting");
        assertThat(result).containsEntry("total", 2);
    }

    @Test
    void shouldListOnlyMatchingNamespacesWhenFilteringByQuery() {
        String tenant = newTenant();
        String root = "company" + IdUtils.create().toLowerCase();
        createFlow(tenant, root + ".team.etl");
        createFlow(tenant, root + ".sales.crm");

        Map<String, Object> result = ToolCalls.call(tool, "list_namespaces", """
            { "QUERY": { "operator": "EQUALS", "value": "TEAM" } }""", tenant);

        assertThat(namespaces(result)).extracting(namespace -> namespace.get("id"))
            .containsExactly(root + ".team", root + ".team.etl");
        assertThat(result).containsEntry("total", 2);
    }

    @Test
    void shouldPageNamespacesWhenMoreThanOnePageMatches() {
        String tenant = newTenant();
        String root = "company" + IdUtils.create().toLowerCase();
        for (int i = 0; i < ListNamespacesTool.PAGE_SIZE; i++) {
            createFlow(tenant, root + ".team%03d".formatted(i));
        }

        Map<String, Object> firstPage = ToolCalls.call(tool, "list_namespaces", "{}", tenant);
        Map<String, Object> secondPage = ToolCalls.call(tool, "list_namespaces", """
            { "page": 2 }""", tenant);

        assertThat(namespaces(firstPage)).hasSize(ListNamespacesTool.PAGE_SIZE)
            .first().satisfies(namespace -> assertThat(namespace).containsEntry("id", root));
        assertThat(firstPage).containsEntry("total", ListNamespacesTool.PAGE_SIZE + 2).containsEntry("page", 1);
        assertThat(namespaces(secondPage)).extracting(namespace -> namespace.get("id"))
            .containsExactly(root + ".team%03d".formatted(ListNamespacesTool.PAGE_SIZE - 1), "system");
        assertThat(secondPage).containsEntry("page", 2);
    }

    @Test
    void shouldRejectPageWhenBelowOne() {
        assertThatThrownBy(() -> ToolCalls.call(tool, "list_namespaces", """
            { "page": 0 }""", newTenant()))
            .hasMessageContaining("Page must be 1 or greater");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> namespaces(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("namespaces");
    }

    private static String newTenant() {
        return "ns" + IdUtils.create().toLowerCase();
    }

    private void createFlow(final String tenant, final String namespace) {
        flowRepository.create(GenericFlow.fromYaml(tenant, """
            id: flow%s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(IdUtils.create().toLowerCase(), namespace)));
    }
}
