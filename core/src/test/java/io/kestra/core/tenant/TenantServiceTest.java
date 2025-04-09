package io.kestra.core.tenant;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TenantServiceTest {
    @Inject
    private TenantService tenantService;

    @Test
    void test() {
        var tenant = tenantService.resolveTenant();
        assertThat(tenant).isNull();
    }

}