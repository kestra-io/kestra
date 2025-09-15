package io.kestra.core.repositories;

public interface TenantMigrationInterface {

    void migrateTenant(String tenantId, boolean dryRun);

}
