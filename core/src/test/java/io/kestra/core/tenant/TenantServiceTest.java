package io.kestra.core.tenant;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class TenantServiceTest {
    @Inject
    private TenantService tenantService;

    @Test
    void test() {
        var tenant = tenantService.resolveTenant();
        assertThat(tenant).isEqualTo("main");
    }

}