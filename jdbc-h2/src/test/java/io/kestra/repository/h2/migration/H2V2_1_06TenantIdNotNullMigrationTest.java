package io.kestra.repository.h2.migration;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
class H2V2_1_06TenantIdNotNullMigrationTest {

    @Inject
    V2_1_06TenantIdNotNullMigration migration;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void migrationIsIdempotent() {
        assertThatCode(() -> {
            migration.migrate();
            migration.migrate();
        }).doesNotThrowAnyException();
    }

    @Test
    void tenantIdNotNullIsEnforced() {
        Flow validFlow = Flow.builder()
            .tenantId("main")
            .id("valid-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .build();

        assertThatCode(() -> flowRepository.create(GenericFlow.of(validFlow))).doesNotThrowAnyException();

        Flow invalidFlow = Flow.builder()
            .id("invalid-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .build();

        assertThatThrownBy(() -> flowRepository.create(GenericFlow.of(invalidFlow)))
            .hasMessageContaining("tenant_id");
    }
}
