package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.serializers.JacksonMapper;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class ListenersTestTask extends Task implements RunnableTask {
    @SuppressWarnings("unchecked")
    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        return RunOutput.builder()
            .outputs(ImmutableMap.of("return", execution.toString()))
            .build();
    }
}
