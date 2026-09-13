package io.kestra.core.junit.extensions;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@link AbstractLoaderExtension} against the directory regression: {@code @LoadFlows} must
 * accept a directory path and load every flow it contains (not just single files). Without the fix,
 * the metastore-wait loop tries to parse the directory itself as a flow and fails.
 */
@KestraTest
class FlowLoaderDirectoryTest {

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    @LoadFlows({ "flows/loaddir" })
    void shouldLoadEveryFlowInDirectory() {
        List<String> ids = flowRepository.findAllForAllTenants().stream()
            .filter(flow -> MAIN_TENANT.equals(flow.getTenantId()))
            .filter(flow -> "io.kestra.tests.loaddir".equals(flow.getNamespace()))
            .map(FlowInterface::getId)
            .toList();

        assertThat(ids).containsExactlyInAnyOrder("load-directory-first", "load-directory-second");
    }
}
