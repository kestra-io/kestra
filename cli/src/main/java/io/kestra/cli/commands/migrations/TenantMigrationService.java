package io.kestra.cli.commands.migrations;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import com.github.javaparser.utils.Log;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.repositories.TenantMigrationInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class TenantMigrationService {

    @Inject
    private TenantMigrationInterface tenantMigrationInterface;

    public void migrateTenant(String tenantId, String tenantName, boolean dryRun) {
        if (StringUtils.isNotBlank(tenantId) && !MAIN_TENANT.equals(tenantId)){
            throw new KestraRuntimeException("Tenant configuration is an enterprise feature. It can only be main in OSS");
        }

        Log.info("🔁 Starting tenant migration...");
        tenantMigrationInterface.migrateTenant(MAIN_TENANT, dryRun);
    }

}
