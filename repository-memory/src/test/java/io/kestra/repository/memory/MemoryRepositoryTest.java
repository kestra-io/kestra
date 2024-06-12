package io.kestra.repository.memory;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class MemoryRepositoryTest {

    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Test
    void verifyMemoryFallbacksToH2() {
        assertThat(flowRepositoryInterface.findAll(null).size(), is(0));

        String flowSource = """
            id: some-flow
            namespace: some.namespace
            tasks:
              - id: some-task
                type: io.kestra.core.tasks.debugs.Return
                format: "Hello, World!\"""";
        Flow flow = yamlFlowParser.parse(flowSource, Flow.class);
        flowRepositoryInterface.create(flow, flowSource, flow);

        assertThat(flowRepositoryInterface.findAll(null).size(), is(1));

        assertThat(flowRepositoryInterface.findByIdWithSource(null, "some.namespace", "some-flow").get().getSource(), is(flowSource));
    }
}
