package io.kestra.core.models.hierarchies;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.GraphUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates the fixtures the UI's client-side graph builder is tested against
 * (ui/packages/topology/src/utils/flowGraphBuilder.test.ts), and fails when a committed one is stale.
 */
class FlowGraphContractTest {
    private static final String UPDATE_VARIABLE = "KESTRA_UPDATE_FLOW_GRAPH_FIXTURES";
    private static final String REGENERATE = "Regenerate it with `%s=true ./gradlew :core:test --tests \"FlowGraphContractTest\"`, then run the topology unit tests."
        .formatted(UPDATE_VARIABLE);
    private static final String FIXTURES = "ui/packages/topology/src/utils/flowGraphFixtures";
    private static final String CORE_PLUGIN_PACKAGE = "io.kestra.plugin.core.";
    private static final List<String> POINT_TYPES = List.of("GraphClusterRoot", "GraphClusterEnd", "GraphClusterFinally", "GraphClusterAfterExecution");
    private static final List<String> FLOWS = List.of(
        "flows/valids/return.yaml",
        "flows/valids/sequential.yaml",
        "flows/valids/errors.yaml",
        "flows/valids/parallel.yaml",
        "flows/valids/parallel-nested.yaml",
        "flows/valids/switch.yaml",
        "flows/valids/approval-basic.yaml",
        "flows/valids/loop-nested.yaml",
        "flows/valids/all-flowable.yaml",
        "flows/valids/trigger-flow-listener.yaml",
        "flows/valids/trigger-flow-listener-no-inputs.yaml",
        "flows/valids/dag.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/finally-sequential.yaml",
        "flows/valids/finally-sequential-error.yaml",
        "flows/valids/finally-dag.yaml",
        "flows/valids/after-execution.yaml",
        "flows/graphs/flowables.yaml",
        "flows/graphs/approvals.yaml",
        "flows/graphs/dag-nested.yaml",
        "flows/graphs/flow-sections.yaml",
        "flows/graphs/triggers-empty.yaml"
    );
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    static Stream<String> flows() {
        return FLOWS.stream();
    }

    @ParameterizedTest
    @MethodSource("flows")
    void shouldMatchTheCommittedFixtureWhenBuildingTheGraphOfAFlow(String flowPath) throws Exception {
        String source = readResource(flowPath);
        FlowWithSource flow = YamlParser.parse(source, FlowWithSource.class);

        ObjectNode fixture = MAPPER.createObjectNode();
        fixture.put("source", source);
        fixture.set("graph", contractView(MAPPER.valueToTree(GraphUtils.flowGraph(flow, null))));

        assertFixture(flowFixture(flowPath), fixture);
    }

    @Test
    void shouldMatchTheCommittedFixtureWhenListingTheCoreFlowableTypes() throws Exception {
        List<String> types = DefaultPluginRegistry.getOrCreate().plugins().stream()
            .flatMap(FlowGraphContractTest::flowableTypes)
            .filter(type -> type.startsWith(CORE_PLUGIN_PACKAGE))
            .distinct()
            .sorted()
            .toList();

        assertFixture(fixturesDirectory().resolve("coreFlowableTypes.json"), MAPPER.valueToTree(types));
    }

    @Test
    void shouldHaveNoFixtureLeftWithoutItsFlow() throws Exception {
        List<String> expected = FLOWS.stream().map(flowPath -> flowFixture(flowPath).getFileName().toString()).sorted().toList();

        try (Stream<Path> files = Files.list(fixturesDirectory().resolve("flows"))) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted().toList())
                .as("A fixture no flow generates is not checked against the backend: delete it.")
                .isEqualTo(expected);
        }
    }

    private static Stream<String> flowableTypes(RegisteredPlugin plugin) {
        return Stream.concat(
            plugin.getTasks().stream().filter(FlowableTask.class::isAssignableFrom).map(Class::getName),
            plugin.getAliases().values().stream().filter(alias -> FlowableTask.class.isAssignableFrom(alias.getValue())).map(Map.Entry::getKey)
        );
    }

    /**
     * What the UI builder has to reproduce: every uid, type, branch and relation, with a task or trigger reduced to
     * its id and type, since the rest of its body is the plugin model serialized back and not the flow source.
     */
    private static ObjectNode contractView(JsonNode graph) {
        Map<String, String> uids = new HashMap<>();
        for (JsonNode node : graph.get("nodes")) {
            String uid = node.get("uid").asText();
            // the suffix of these uids is drawn at random on every build
            boolean isPoint = POINT_TYPES.stream().anyMatch(type -> node.get("type").asText().endsWith("." + type));
            uids.put(uid, isPoint ? uid.replaceFirst("-[A-Za-z0-9]+$", "-*") : uid);
        }

        ObjectNode view = MAPPER.createObjectNode();
        view.set("nodes", sorted(graph.get("nodes"), node -> nodeView(node, uids), "uid"));
        view.set("edges", sorted(graph.get("edges"), edge -> edgeView(edge, uids), "source", "target"));
        view.set("clusters", sorted(graph.get("clusters"), cluster -> clusterView(cluster, uids), "start"));
        return view;
    }

    private static ObjectNode nodeView(JsonNode node, Map<String, String> uids) {
        ObjectNode view = MAPPER.createObjectNode();
        view.put("uid", uids.getOrDefault(node.get("uid").asText(), node.get("uid").asText()));
        copy(node, view, "type", "branchType", "relationType", "disabled");
        for (String reference : List.of("task", "triggerDeclaration")) {
            if (node.hasNonNull(reference)) {
                copy(node.get(reference), view.putObject(reference), "id", "type");
            }
        }
        return view;
    }

    private static ObjectNode edgeView(JsonNode edge, Map<String, String> uids) {
        ObjectNode view = MAPPER.createObjectNode();
        view.put("source", uids.get(edge.get("source").asText()));
        view.put("target", uids.get(edge.get("target").asText()));
        copy(edge.get("relation"), view.putObject("relation"), "relationType", "value");
        return view;
    }

    private static ObjectNode clusterView(JsonNode cluster, Map<String, String> uids) {
        ObjectNode view = MAPPER.createObjectNode();
        ObjectNode clusterNode = view.putObject("cluster");
        copy(cluster.get("cluster"), clusterNode, "uid", "type", "branchType", "relationType");
        if (cluster.get("cluster").hasNonNull("taskNode")) {
            clusterNode.set("taskNode", nodeView(cluster.get("cluster").get("taskNode"), uids));
        }

        List<String> nodes = new ArrayList<>();
        cluster.get("nodes").forEach(uid -> nodes.add(uids.getOrDefault(uid.asText(), uid.asText())));
        view.set("nodes", MAPPER.valueToTree(nodes.stream().sorted().toList()));
        view.set("parents", cluster.get("parents"));
        view.put("start", uids.get(cluster.get("start").asText()));
        view.put("end", uids.get(cluster.get("end").asText()));
        return view;
    }

    private static void copy(JsonNode from, ObjectNode to, String... fields) {
        for (String field : fields) {
            if (from.hasNonNull(field)) {
                to.set(field, from.get(field));
            }
        }
    }

    /** Order is not part of the contract, and a Switch or an Approval iterates its cases in hash order. */
    private static ArrayNode sorted(JsonNode items, Function<JsonNode, ObjectNode> view, String... keys) {
        List<ObjectNode> views = new ArrayList<>();
        items.forEach(item -> views.add(view.apply(item)));
        views.sort(Comparator.comparing(item -> Stream.of(keys).map(key -> item.get(key).asText()).toList().toString()));
        return MAPPER.createArrayNode().addAll(views);
    }

    private static void assertFixture(Path fixture, JsonNode expected) throws IOException {
        if ("true".equals(System.getenv(UPDATE_VARIABLE))) {
            Files.createDirectories(fixture.getParent());
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));
            Files.writeString(fixture, MAPPER.writer(printer).writeValueAsString(expected) + "\n");
            return;
        }

        assertThat(fixture).as("The fixture '%s' is missing. %s", fixture.getFileName(), REGENERATE).exists();
        assertThat(MAPPER.readTree(Files.readString(fixture)))
            .as("The fixture '%s' is stale. %s", fixture.getFileName(), REGENERATE)
            .isEqualTo(expected);
    }

    private static Path flowFixture(String flowPath) {
        String name = Path.of(flowPath).getFileName().toString().replaceFirst("\\.ya?ml$", ".json");
        return fixturesDirectory().resolve("flows").resolve(name);
    }

    private static Path fixturesDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        while (!Files.exists(directory.resolve("settings.gradle"))) {
            directory = Objects.requireNonNull(directory.getParent(), "The test must run inside the repository.");
        }
        return directory.resolve(FIXTURES);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = FlowGraphContractTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(stream, path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
