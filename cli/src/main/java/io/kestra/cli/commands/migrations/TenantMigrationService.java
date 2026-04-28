package io.kestra.cli.commands.migrations;

import io.kestra.core.utils.ListUtils;
import org.apache.commons.lang3.StringUtils;

import com.github.javaparser.utils.Log;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TenantMigrationInterface;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

@Singleton
@Slf4j
public class TenantMigrationService {

    @Inject
    private TenantMigrationInterface tenantMigrationInterface;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<FlowInterface> flowQueue;

    public void migrateTenant(String tenantId, String tenantName, boolean dryRun, boolean restoreQueue, List<String> excludes) {
        if (StringUtils.isNotBlank(tenantId) && !MAIN_TENANT.equals(tenantId)) {
            throw new KestraRuntimeException("Tenant configuration is an enterprise feature. It can only be main in OSS");
        }

        Log.info("🔁 Starting tenant migration...");
        if (!ListUtils.isEmpty(excludes)) {
            Log.info("The following data will be excluded from migration: " + excludes);
        }
        tenantMigrationInterface.migrateTenant(MAIN_TENANT, dryRun, excludes);
        if (restoreQueue) {
            migrateQueue(dryRun);
        }
    }

    protected void migrateQueue(boolean dryRun) {
        if (!dryRun) {
            log.info("🔁 Starting restoring queue...");
            flowRepository.findAllWithSourceForAllTenants().forEach(flow ->
            {
                try {
                    flowQueue.emit(flow);
                } catch (QueueException e) {
                    log.warn("Unable to send the flow {} to the queue", flow.uid(), e);
                }
            });
        }
    }

}
