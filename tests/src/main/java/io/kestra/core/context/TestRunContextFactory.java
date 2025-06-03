package io.kestra.core.context;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class TestRunContextFactory extends RunContextFactory {

    @VisibleForTesting
    public RunContext of() {
        return of(Map.of("flow", Map.of("id", "id", "namespace", "namespace", "tenantId", MAIN_TENANT)));
    }
}
