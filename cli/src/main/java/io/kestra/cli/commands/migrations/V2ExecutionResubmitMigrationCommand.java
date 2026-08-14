package io.kestra.cli.commands.migrations;

import java.util.List;

import com.github.javaparser.utils.Log;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;

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
        DispatchQueueInterface<Execution> executionQueue = applicationContext.getBean(DispatchQueueInterface.class, Qualifiers.byTypeArguments(Execution.class));

        Log.info("🔁 Starting running and created execution resubmission...");
        List<String> tenants = tenantService.listTenants();
        List<State.Type> states = List.of(State.Type.RUNNING, State.Type.CREATED);
        tenants.forEach(tenant ->
        {
            Log.info("Resubmitting executions for tenant: " + tenant);
            // every kind must be resubmitted, including the loop iterations that the executions search hides
            long count = repository.find(null, tenant, null, null, null, null, null, states, null, null)
                .doOnNext(execution ->
                {
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
