package io.kestra.cli.services;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.kestra.core.exceptions.KestraRuntimeException;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class TenantIdSelectorService {

    //For override purpose in Kestra EE
    public String getTenantId(String tenantId) {
        if (StringUtils.isNotBlank(tenantId) && !MAIN_TENANT.equals(tenantId)){
            throw new KestraRuntimeException("Tenant id can only be 'main'");
        }
        return MAIN_TENANT;
    }

    public String getTenantIdAndAllowEETenants(String tenantId) {
        if (StringUtils.isNotBlank(tenantId)){
            return tenantId;
        }
        return MAIN_TENANT;
    }
}
