package io.kestra.plugin.core.trigger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.scheduler.TriggerEventQueue;
import io.kestra.scheduler.events.SetDisableTrigger;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.micronaut.context.ApplicationContext;
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
                    type: BOOL
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
        title = "The flow identifier of the trigger to toggle",
        description = "If not set, the current flow identifier will be used."
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The namespace of the flow of the trigger to toggle",
        description = "If not set, the current flow namespace will be used."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @Schema(title = "The identifier of the trigger to toggle")
    @NotNull
    @PluginProperty(dynamic = true)
    private String trigger;

    @Schema(title = "Whether to enable or disable the trigger")
    @NotNull
    @Builder.Default
    @PluginProperty
    private Boolean enabled = false;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {

        String realNamespace = namespace == null ? runContext.flowInfo().namespace() : runContext.render(namespace);
        String realFlowId = flowId == null ? runContext.flowInfo().id() : runContext.render(flowId);
        String realTrigger = runContext.render(trigger);

        // verify that the target flow exists, and the current execution is authorized to access it
        final ApplicationContext applicationContext = ((DefaultRunContext) runContext).getApplicationContext();
        FlowMetaStoreInterface flowExecutor = applicationContext.getBean(FlowMetaStoreInterface.class);
        flowExecutor.findByIdFromTask(
            runContext.flowInfo().tenantId(),
            realNamespace,
            realFlowId,
            Optional.empty(),
            runContext.flowInfo().tenantId(),
            runContext.flowInfo().namespace(),
            runContext.flowInfo().id()
        )
        .orElseThrow(() -> new IllegalArgumentException("Unable to find flow " + realNamespace + "." + realFlowId + ". Make sure the flow exists and the current execution is authorized to access it."));
        
        TriggerEventQueue queue = applicationContext.getBean(TriggerEventQueue.class);
        queue.send(new SetDisableTrigger(TriggerId.of(runContext.flowInfo().tenantId(), realNamespace, realFlowId, realTrigger), !enabled));
        
        return null;
    }
}
