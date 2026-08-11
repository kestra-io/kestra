package io.kestra.core.tasks.test;

import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.plugin.core.flow.Subflow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Test executable task that generates an exception on createWorkerTaskResult"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "no example here"
            }
        )
    }
)
public class BadExecutable extends Subflow {

    @Override
    public Optional<SubflowExecutionResult> createSubflowExecutionResult(RunContext runContext, TaskRun taskRun, FlowInterface flow, Execution execution, Map<String, Object> outputs) {
        throw new RuntimeException("An error!");
    }
}
