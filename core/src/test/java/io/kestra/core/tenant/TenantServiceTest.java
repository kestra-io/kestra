package io.kestra.core.tenant;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class TenantServiceTest {
    @Inject
    private TenantService tenantService;

    @Test
    void resolveTenant() {
        var tenant = tenantService.resolveTenant();
        assertThat(tenant, nullValue());
    }

    @Test
    void storageConfiguration() {
        var storageConfiguration = tenantService.storageConfiguration("tenantId");
        assertTrue(storageConfiguration.isEmpty());
    }
}