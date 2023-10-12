package io.kestra.core.tenant;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

@MicronautTest
class TenantServiceTest {
    @Inject
    private TenantService tenantService;

    @Test
    void test() {
        var tenant = tenantService.resolveTenant();
        assertThat(tenant, nullValue());
    }

}