package io.kestra.cli.commands.migrations;

import com.github.javaparser.utils.Log;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.List;

@Command(
    name = "triggers",
    description = "migrate all triggers to Kestra 2.0."
)
public class V2TriggerMigrationCommand extends AbstractCommand {
    
    @Inject
    private ApplicationContext applicationContext;
    
    @CommandLine.Option(names = "--dry-run", description = "Preview only, do not update")
    boolean dryRun;
    
    @SuppressWarnings("removal")
    @Override
    public Integer call() throws Exception {
        super.call();
        
        if (dryRun) {
            System.out.println("🧪 Dry-run mode enabled. No changes will be applied.");
        }
        
        Log.info("🔁 Starting trigger states migration...");
        TriggerRepositoryInterface repository = applicationContext.getBean(TriggerRepositoryInterface.class);
        SchedulerConfiguration configuration = applicationContext.getBean(SchedulerConfiguration.class);
        List<Trigger> triggers = repository.findAllForAllTenantsV1();
        Log.info("Found [{}] triggers to migrate.");
        triggers.forEach(trigger -> {
            try {
                TriggerState migrated = trigger.toTriggerState(configuration.vnodes());
                if (!dryRun) {
                    repository.save(migrated);
                }
                System.out.println("✅ Migration complete for: " + TriggerId.of(trigger));
            } catch (Exception e) {
                System.err.println("❌ Migration failed for : " + TriggerId.of(trigger));
                e.printStackTrace();
            }
        });
        System.out.println("✅ Migration complete.");
        return 0;
    }
}
