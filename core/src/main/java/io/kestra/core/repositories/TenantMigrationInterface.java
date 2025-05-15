package io.kestra.core.repositories;

public interface TenantMigrationInterface {

    void migrateTenant(boolean dryRun);

}
