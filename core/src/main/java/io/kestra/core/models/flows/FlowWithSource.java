package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;


@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class FlowWithSource extends Flow {

    String source;

    @SuppressWarnings("deprecation")
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
            ._finally(this._finally)
            .listeners(this.listeners)
            .afterExecution(this.afterExecution)
            .triggers(this.triggers)
            .pluginDefaults(this.pluginDefaults)
            .disabled(this.disabled)
            .deleted(this.deleted)
            .concurrency(this.concurrency)
            .retry(this.retry)
            .sla(this.sla)
            .checks(this.checks)
            .build();
    }

    @Override
    @Schema(hidden = false)
    public String getSource() {
        return this.source;
    }

    @Override
    public FlowWithSource toDeleted() {
        return this.toBuilder()
            .revision(this.revision + 1)
            .deleted(true)
            .build();
    }

    @SuppressWarnings("deprecation")
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
            ._finally(flow._finally)
            .afterExecution(flow.afterExecution)
            .listeners(flow.listeners)
            .triggers(flow.triggers)
            .pluginDefaults(flow.pluginDefaults)
            .disabled(flow.disabled)
            .deleted(flow.deleted)
            .source(source)
            .concurrency(flow.concurrency)
            .retry(flow.retry)
            .sla(flow.sla)
            .checks(flow.checks)
            .build();
    }
}
