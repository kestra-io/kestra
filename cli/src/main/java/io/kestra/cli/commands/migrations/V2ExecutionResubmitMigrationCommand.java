package io.kestra.cli.commands.migrations;

import java.util.List;

import com.github.javaparser.utils.Log;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.ListUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "execution-resubmit",
    description = "Resubmit running and created executions to Kestra 2.0."
)
public class V2ExecutionResubmitMigrationCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Override
    public Integer call() throws Exception {
        super.call();

        ExecutionRepositoryInterface repository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        TenantService tenantService = applicationContext.getBean(TenantService.class);
        TaskOutputService taskOutputService = applicationContext.getBean(TaskOutputService.class);
        DispatchQueueInterface<Execution> executionQueue = applicationContext.getBean(DispatchQueueInterface.class, Qualifiers.byTypeArguments(Execution.class));

        Log.info("🔁 Starting running and created execution resubmission...");
        List<String> tenants = tenantService.listTenants();
        QueryFilter filter = QueryFilter.builder().field(QueryFilter.Field.STATE).value(List.of(State.Type.RUNNING, State.Type.CREATED)).operation(QueryFilter.Op.IN).build();
        tenants.forEach(tenant ->
        {
            Log.info("Resubmitting executions for tenant: " + tenant);
            long count = repository.findAsync(tenant, List.of(filter))
                .doOnNext(execution ->
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

                    // resubmit to the queue
                    try {
                        executionQueue.emit(execution);
                    } catch (QueueException e) {
                        Log.error(e, "Failed to resubmit execution: " + execution.getId());
                    }
                })
                .count()
                .blockOptional()
                .orElse(0L);

            System.out.println("✅ Migration complete for tenant '" + tenant + "': " + count + " executions resubmitted.");
        });

        System.out.println("✅ Migration complete.");
        return 0;
    }
}
