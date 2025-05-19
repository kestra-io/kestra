package io.kestra.cli.commands.migrations;

import com.github.javaparser.utils.Log;
import io.kestra.core.repositories.TenantMigrationInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TenantMigrationService {

    @Inject
    private TenantMigrationInterface tenantMigrationInterface;

    public void migrateTenant(String tenantId, String tenantName, boolean dryRun) {
        Log.info("🔁 Starting tenant migration...");
        tenantMigrationInterface.migrateTenant("main", dryRun);
    }

}
