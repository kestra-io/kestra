package io.kestra.plugin.core.trigger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Toggle a trigger: enable or disable it."
)
@Plugin(
    examples = {
        @Example(
            title = "Toggle a trigger on flow input.",
            full = true,
            code = """
                id: trigger_toggle
                namespace: company.team

                inputs:
                  - id: toggle
                    type: BOOLEAN
                    defaults: true

                tasks:
                  - id: if
                    type: io.kestra.plugin.core.flow.If
                    condition: "{{inputs.toggle}}"
                    then:
                      - id: enable
                        type: io.kestra.plugin.core.trigger.Toggle
                        trigger: schedule
                        enabled: true
                    else:
                      - id: disable
                        type: io.kestra.plugin.core.trigger.Toggle
                        trigger: schedule
                        enabled: false
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "* * * * *"
                """
        )
    },
    aliases = "io.kestra.core.tasks.trigger.Toggle"
)
public class Toggle extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "The flow identifier of the trigger to toggle.",
        description = "If not set, the current flow identifier will be used."
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The namespace of the flow of the trigger to toggle.",
        description = "If not set, the current flow namespace will be used."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @Schema(title = "The identifier of the trigger to toggle.")
    @NotNull
    @PluginProperty(dynamic = true)
    private String trigger;

    @Schema(title = "Whether to enable or disable the trigger.")
    @NotNull
    @Builder.Default
    @PluginProperty
    private Boolean enabled = false;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        Map<String, String> flowVariables = (Map<String, String>) runContext.getVariables().get("flow");
        String realNamespace = namespace == null ? flowVariables.get("namespace") : runContext.render(namespace);
        String realFlowId = flowId == null ? flowVariables.get("id") : runContext.render(flowId);
        String realTrigger = runContext.render(trigger);

        // verify that the target flow exists, and the current execution is authorized to access it
        final ApplicationContext applicationContext = ((DefaultRunContext) runContext).getApplicationContext();
        FlowExecutorInterface flowExecutor = applicationContext.getBean(FlowExecutorInterface.class);
        flowExecutor.findByIdFromTask(
            runContext.tenantId(),
            realNamespace,
            realFlowId,
            Optional.empty(),
            runContext.tenantId(),
            flowVariables.get("namespace"),
            flowVariables.get("id")
        )
            .orElseThrow(() -> new IllegalArgumentException("Unable to find flow " + realNamespace + "." + realFlowId + ". Make sure the flow exists and the current execution is authorized to access it."));


        // load the trigger from the database
        TriggerContext triggerContext = TriggerContext.builder()
            .tenantId(runContext.tenantId())
            .namespace(realNamespace)
            .flowId(realFlowId)
            .triggerId(realTrigger)
            .build();
        TriggerRepositoryInterface triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
        Trigger currentTrigger = triggerRepository.findLast(triggerContext).orElseThrow(() -> new IllegalArgumentException("Unable to find trigger " + realTrigger + " for the flow " + realNamespace + "." + realFlowId));
        currentTrigger = currentTrigger.toBuilder().disabled(!enabled).build();

        // update the trigger by emitting inside the queue
        QueueInterface<Trigger> triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        triggerQueue.emit(currentTrigger);

        return null;
    }
}
