package io.kestra.repository.memory;

import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class MemoryRepositoryTest {

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
                format: "Hello, World!"
         """;
        flowRepositoryInterface.create(GenericFlow.fromYaml(null, flowSource));

        assertThat(flowRepositoryInterface.findAll(null).size(), is(1));

        assertThat(flowRepositoryInterface.findByIdWithSource(null, "some.namespace", "some-flow").get().getSource(), is(flowSource));
    }
}
