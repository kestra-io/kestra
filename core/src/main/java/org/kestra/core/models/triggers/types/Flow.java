package org.kestra.core.models.triggers.types;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionTrigger;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.runners.RunContext;
import org.kestra.core.utils.IdUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Flow extends AbstractTrigger {
    @Nullable
    private Map<String, Object> inputs;

    public Optional<Execution> evaluate(RunContext runContext, org.kestra.core.models.flows.Flow flow, Execution current) {
        Logger logger = runContext.logger();

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .trigger(ExecutionTrigger.builder()
                .id(this.id)
                .type(this.type)
                .variables(ImmutableMap.of(
                    "executionId", current.getId()
                ))
                .build()
            );

        try {
            builder.inputs(runContext.render(this.inputs == null ? new HashMap<>() : this.inputs));
            return Optional.of(builder.build());
        } catch (Exception e) {
            logger.warn(
                "Failed to trigger flow {}.{} for trigger {}, invalid inputs",
                flow.getNamespace(),
                flow.getId(),
                this.getId(),
                e
            );

            return Optional.empty();
        }
    }
}
