package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.plugin.core.flow.Subflow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

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
    public Optional<SubflowExecutionResult> createSubflowExecutionResult(RunContext runContext, TaskRun taskRun, Flow flow, Execution execution) {
        throw new RuntimeException("An error!");
    }
}
