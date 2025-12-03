package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class NamespaceServiceTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private NamespaceService namespaceService;

    @Test
    void isNamespaceExists() {
        Flow flow = Flow.builder().id("test").namespace("io.kestra").tenantId(TenantService.MAIN_TENANT).build();
        flowRepository.create(GenericFlow.of(flow));

        assertThat(namespaceService.isNamespaceExists(TenantService.MAIN_TENANT, "io.kestra")).isTrue();
    }

    @Test
    void isNamespaceExistsShouldReturnFalseWhenNotFond() {
        assertThat(namespaceService.isNamespaceExists(TenantService.MAIN_TENANT, "notFound")).isFalse();
    }

    @Test
    void isNamespaceAllowed() {
        assertThat(namespaceService.requireExistingNamespace(TenantService.MAIN_TENANT, "io.kestra")).isFalse();
    }

    @Test
    void isAllowedNamespace() {
        assertTrue(namespaceService.isAllowedNamespace("tenant", "namespace", "fromTenant", "fromNamespace"));
    }

    @Test
    void checkAllowedNamespace() {
        assertDoesNotThrow(() -> namespaceService.checkAllowedNamespace("tenant", "namespace", "fromTenant", "fromNamespace"));
    }

    @Test
    void areAllowedAllNamespaces() {
        assertTrue(namespaceService.areAllowedAllNamespaces("tenant", "fromTenant", "fromNamespace"));
    }

    @Test
    void checkAllowedAllNamespaces() {
        assertDoesNotThrow(() -> namespaceService.checkAllowedAllNamespaces("tenant", "fromTenant", "fromNamespace"));
    }

}