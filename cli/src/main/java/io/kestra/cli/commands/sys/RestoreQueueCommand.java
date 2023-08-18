package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.services.RestoreQueueService;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "restore-queue",
    description = {"send all data from a repository to kafka.",
        "Mostly useful to send all flows, templates & triggers from repository to kafka in case of restore."
    }
)
@Slf4j
public class RestoreQueueCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"--no-recreate"}, description = "Don't drop the topic and recreate it")
    private boolean noRecreate = false;

    @CommandLine.Option(names = {"--no-flows"}, description = "Don't send flows")
    private boolean noFlows = false;

    @CommandLine.Option(names = {"--no-templates"}, description = "Don't send templates")
    private boolean noTemplates = false;

    @CommandLine.Option(names = {"--no-triggers"}, description = "Don't send triggers")
    private boolean noTriggers = false;

    @CommandLine.Option(names = {"--no-triggers-execution-id"}, description = "Remove executionId from trigger")
    private boolean noTriggerExecutionId = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        RestoreQueueService restoreQueueService = applicationContext.getBean(RestoreQueueService.class);

        // flows
        if (!noFlows) {
            int size = restoreQueueService.flows(noRecreate);
            stdOut("Successfully send {0} flows", size);
        }

        // templates
        if (!this.noTemplates && applicationContext.containsBean(TemplateRepositoryInterface.class)) {
            int size = restoreQueueService.templates(noRecreate);
            stdOut("Successfully send {0} templates", size);
        }

        // trigger
        if (!this.noTriggers) {
            int size = restoreQueueService.triggers(noRecreate, noTriggerExecutionId);
            stdOut("Successfully send {0} triggers", size);
        }

        return 0;
    }
}
