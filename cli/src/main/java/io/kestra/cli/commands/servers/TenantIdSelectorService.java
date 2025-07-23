package io.kestra.cli.commands.servers;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import jakarta.inject.Singleton;

@Singleton
public class TenantIdSelectorService {

    //For override purpose in Kestra EE
    public String getTenantId(String tenantId) {
        return MAIN_TENANT;
    }
}
