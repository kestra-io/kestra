package io.kestra.core.repositories;

import java.util.List;

public interface TenantMigrationInterface {

    void migrateTenant(String tenantId, boolean dryRun, List<String> excludes);

}
