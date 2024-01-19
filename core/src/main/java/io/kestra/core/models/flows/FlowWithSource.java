package io.kestra.core.models.flows;

import io.kestra.core.services.FlowService;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class FlowWithSource extends Flow {
    String source;

    public Flow toFlow() {
        return Flow.builder()
            .tenantId(this.tenantId)
            .id(this.id)
            .namespace(this.namespace)
            .revision(this.revision)
            .description(this.description)
            .labels(this.labels)
            .inputs(this.inputs)
            .outputs(this.outputs)
            .variables(this.variables)
            .tasks(this.tasks)
            .errors(this.errors)
            .listeners(this.listeners)
            .triggers(this.triggers)
            .taskDefaults(this.taskDefaults)
            .disabled(this.disabled)
            .deleted(this.deleted)
            .concurrency(this.concurrency)
            .build();
    }

    public String getSource() {
        String source = this.source;

        // previously, we insert source on database keeping default value (like deleted, ...)
        // if the previous serialization is the same as actual one, we use a clean version removing them
        Flow flow = toFlow();
        source = FlowService.generateSource(flow, source);

        // same here but with version that don't make any sense on the source code, so removing it
        return cleanupSource(source);
    }

    private static String cleanupSource(String source) {
        return source.replaceFirst("(?m)^revision: \\d+\n?","");
    }

    public boolean isUpdatable(Flow flow, String flowSource) {
        return this.equalsWithoutRevision(flow) &&
            this.source.equals(cleanupSource(flowSource));
    }

    public static FlowWithSource of(Flow flow, String source) {
        return FlowWithSource.builder()
            .tenantId(flow.tenantId)
            .id(flow.id)
            .namespace(flow.namespace)
            .revision(flow.revision)
            .description(flow.description)
            .labels(flow.labels)
            .inputs(flow.inputs)
            .outputs(flow.outputs)
            .variables(flow.variables)
            .tasks(flow.tasks)
            .errors(flow.errors)
            .listeners(flow.listeners)
            .triggers(flow.triggers)
            .taskDefaults(flow.taskDefaults)
            .disabled(flow.disabled)
            .deleted(flow.deleted)
            .source(source)
            .concurrency(flow.concurrency)
            .build();
    }
}
