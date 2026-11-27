package io.kestra.cli.services;

import io.kestra.core.exceptions.KestraRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdSelectorServiceTest {
    private final TenantIdSelectorService service = new TenantIdSelectorService();

    @Test
    void shouldReturnMainTenantWhenTenantIdIsMain() {
        String tenantId = service.getTenantId(MAIN_TENANT);

        assertThat(tenantId).isEqualTo(MAIN_TENANT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReturnMainTenantWhenTenantIdIsBlank(String tenantId) {
        assertThat(service.getTenantId(tenantId)).isEqualTo(MAIN_TENANT);
    }

    @Test
    void shouldThrowExceptionWhenTenantIdIsNotMain() {
        assertThatThrownBy(() -> service.getTenantId("custom-tenant"))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("Tenant id can only be 'main'");
    }

    @Test
    void shouldReturnCustomTenantWhenAllowEETenantsAndTenantIsNotBlank() {
        String tenantId = service.getTenantIdAndAllowEETenants("custom-tenant");

        assertThat(tenantId).isEqualTo("custom-tenant");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReturnMainTenantWhenAllowEETenantsAndTenantIsBlank(String tenantId) {
        assertThat(service.getTenantIdAndAllowEETenants(tenantId)).isEqualTo(MAIN_TENANT);
    }

    @Test
    void shouldDoNothingWhenCreateTenantCalled() {
        assertThatCode(() -> service.createTenant("any-tenant"))
            .doesNotThrowAnyException();
    }
}
