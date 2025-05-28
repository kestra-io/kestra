package io.kestra.repository.memory;

import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class MemoryRepositoryTest {

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Test
    void verifyMemoryFallbacksToH2() {
        assertThat(flowRepositoryInterface.findAll(null).size()).isZero();

        String flowSource = """
            id: some-flow
            namespace: some.namespace
            tasks:
              - id: some-task
                type: io.kestra.core.tasks.debugs.Return
                format: "Hello, World!"
         """;
        flowRepositoryInterface.create(GenericFlow.fromYaml(MAIN_TENANT, flowSource));

        assertThat(flowRepositoryInterface.findAll(MAIN_TENANT).size()).isEqualTo(1);

        assertThat(flowRepositoryInterface.findByIdWithSource(MAIN_TENANT, "some.namespace", "some-flow").get().getSource()).isEqualTo(flowSource);
    }
}
