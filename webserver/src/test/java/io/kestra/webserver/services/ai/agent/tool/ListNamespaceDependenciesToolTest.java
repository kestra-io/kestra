package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(environments = "memory")
class ListNamespaceDependenciesToolTest {
    @Inject
    private ListNamespaceDependenciesTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Test
    void shouldListDependenciesOfNamespaceAndChildrenWhenInvoked() {
        String tenant = newTenant();
        String root = "company" + IdUtils.create().toLowerCase();
        FlowNode parent = createFlow(tenant, root + ".team");
        FlowNode child = createFlow(tenant, root + ".team.etl");
        FlowNode outside = createFlow(tenant, root + ".sales");
        FlowNode sibling = createFlow(tenant, root + ".teamx");
        saveDependency(parent, child, FlowRelation.FLOW_TASK);
        saveDependency(child, outside, FlowRelation.FLOW_TRIGGER);
        saveDependency(sibling, outside, FlowRelation.FLOW_TASK);

        Map<String, Object> result = ToolCalls.call(tool, "list_namespace_dependencies", """
            { "namespace": "%s" }""".formatted(root + ".team"), tenant);

        assertThat(dependencies(result))
            .extracting(
                dependency -> ref(dependency, "upstream").get("id"),
                dependency -> ref(dependency, "downstream").get("namespace"),
                dependency -> ref(dependency, "downstream").get("id"),
                dependency -> dependency.get("relation")
            )
            .containsExactly(
                tuple(parent.getId(), root + ".team.etl", child.getId(), "FLOW_TASK"),
                tuple(child.getId(), root + ".sales", outside.getId(), "FLOW_TRIGGER")
            );
        assertThat(result).containsEntry("total", 2).containsEntry("page", 1);
    }

    @Test
    void shouldPageDependenciesWhenMoreThanOnePageExists() {
        String tenant = newTenant();
        String namespace = "company" + IdUtils.create().toLowerCase();
        FlowNode parent = createFlow(tenant, namespace);
        for (int i = 0; i <= ListNamespaceDependenciesTool.PAGE_SIZE; i++) {
            saveDependency(parent, createFlow(tenant, namespace), FlowRelation.FLOW_TASK);
        }

        Map<String, Object> firstPage = ToolCalls.call(tool, "list_namespace_dependencies", """
            { "namespace": "%s" }""".formatted(namespace), tenant);
        Map<String, Object> secondPage = ToolCalls.call(tool, "list_namespace_dependencies", """
            { "namespace": "%s", "page": 2 }""".formatted(namespace), tenant);

        assertThat(dependencies(firstPage)).hasSize(ListNamespaceDependenciesTool.PAGE_SIZE);
        assertThat(firstPage).containsEntry("total", ListNamespaceDependenciesTool.PAGE_SIZE + 1).containsEntry("page", 1);
        assertThat(dependencies(secondPage)).hasSize(1);
        assertThat(secondPage).containsEntry("page", 2);
    }

    @Test
    void shouldFailWhenNamespaceHoldsNoFlow() {
        String tenant = newTenant();
        createFlow(tenant, "companyx");

        assertThatThrownBy(() -> ToolCalls.call(tool, "list_namespace_dependencies", """
            { "namespace": "company" }""", tenant))
            .hasMessageContaining("No flows found in namespace 'company'");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dependencies(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("dependencies");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ref(final Map<String, Object> dependency, final String side) {
        return (Map<String, Object>) dependency.get(side);
    }

    private static String newTenant() {
        return "nsdeps" + IdUtils.create().toLowerCase();
    }

    private FlowNode createFlow(final String tenant, final String namespace) {
        return FlowNode.of(flowRepository.create(GenericFlow.fromYaml(tenant, """
            id: flow%s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(IdUtils.create().toLowerCase(), namespace))));
    }

    private void saveDependency(final FlowNode upstream, final FlowNode downstream, final FlowRelation relation) {
        flowTopologyRepository.save(
            FlowTopology.builder()
                .source(upstream)
                .destination(downstream)
                .relation(relation)
                .build()
        );
    }
}
