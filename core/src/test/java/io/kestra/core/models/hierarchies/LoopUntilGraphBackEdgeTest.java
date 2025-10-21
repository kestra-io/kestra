package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class LoopUntilGraphBackEdgeTest {

    @Test
    void waitFor_hasBackEdgeFromEndToStart() throws IOException, IllegalVariableEvaluationException {
        FlowWithSource flow = parse("flows/valids/waitfor-no-success.yaml");

        FlowGraph flowGraph = io.kestra.core.utils.GraphUtils.flowGraph(flow, null);

        // look for an edge that goes from the LoopUntil/WaitFor cluster end to its start
        boolean hasBackEdge = flowGraph.getEdges().stream().anyMatch(e ->
            e.getSource().matches("root\\.waitfor\\.end-.*") &&
                e.getTarget().matches("root\\.waitfor\\.root-.*")
        );

        assertThat(hasBackEdge)
            .as("Expected an end->start back-edge for waitfor/LoopUntil cluster")
            .isTrue();
    }

    private static FlowWithSource parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, FlowWithSource.class).toBuilder()
            .tenantId(MAIN_TENANT)
            .source(Files.readString(file.toPath()))
            .build();
    }
}
