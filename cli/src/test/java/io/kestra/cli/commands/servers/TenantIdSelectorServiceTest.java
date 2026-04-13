package io.kestra.cli.commands.servers;

import org.junit.jupiter.api.Test;

import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TenantIdSelectorServiceTest {
    @Test
    void shouldFailWithTenantIdSpecified() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            assertThatThrownBy(() -> ctx.getBean(TenantIdSelectorService.class).getTenantId(IdUtils.create()))
                .isInstanceOf(KestraRuntimeException.class)
                .hasMessageContaining("Tenant id can only be 'main'");
        }
    }

}
