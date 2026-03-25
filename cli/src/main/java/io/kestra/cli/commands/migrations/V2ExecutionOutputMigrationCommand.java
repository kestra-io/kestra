package io.kestra.cli.commands.migrations;

import java.util.Collections;
import java.util.List;

import com.github.javaparser.utils.Log;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.ListUtils;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "execution-output",
    description = "Move execution outputs in a new table for Kestra 2.0."
)
public class V2ExecutionOutputMigrationCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(
        names = "--include-running-created",
        description = "Include RUNNING and CREATED executions in the migration. By default they are omitted as the `execution-resubmit` command will move their outputs."
    )
    boolean includeRunningCreated = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        ExecutionRepositoryInterface repository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        TenantService tenantService = applicationContext.getBean(TenantService.class);
        TaskOutputService taskOutputService = applicationContext.getBean(TaskOutputService.class);

        Log.info("🔁 Starting moving execution outputs...");
        List<String> tenants = tenantService.listTenants();
        QueryFilter filter = QueryFilter.builder().field(QueryFilter.Field.STATE).value(List.of(State.Type.RUNNING, State.Type.CREATED)).operation(QueryFilter.Op.NOT_IN).build();
        tenants.forEach(tenant ->
        {
            Log.info("Moving execution outputs for tenant: " + tenant);
            long count = repository.findAsync(tenant, includeRunningCreated ? Collections.emptyList() : List.of(filter))
                .map(execution ->
                {
                    // save outputs in the new table
                    ListUtils.emptyOnNull(execution.getTaskRunList()).forEach(taskRun ->
                    {
                        try {
                            taskOutputService.saveOutputs(taskRun, taskRun.getOutputs());
                        } catch (InternalException e) {
                            Log.error(e, "Failed to save outputs for execution: " + execution.getId() + " task run: " + taskRun.getId());
                        }
                    });
                    return ListUtils.emptyOnNull(execution.getTaskRunList()).size();
                })
                .reduce(0L, (a, b) -> a + b)
                .blockOptional()
                .orElse(0L);

            System.out.println("✅ Migration complete for tenant '" + tenant + "': " + count + " execution which outputs are moved.");
        });

        System.out.println("✅ Migration complete.");
        return 0;
    }
}
