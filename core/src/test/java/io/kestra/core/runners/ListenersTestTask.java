package io.kestra.core.runners;

import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.RetryUtils;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;

import java.time.Duration;
import java.util.NoSuchElementException;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class ListenersTestTask extends Task implements RunnableTask<ListenersTestTask.Output> {
    @Override
    public ListenersTestTask.Output run(RunContext runContext) throws Exception {
        ExecutionRepositoryInterface executionRepository =  ((DefaultRunContext)runContext).getApplicationContext().getBean(ExecutionRepositoryInterface.class);
        RetryUtils.Instance<Execution, NoSuchElementException> retryInstance =  ((DefaultRunContext)runContext).getApplicationContext().getBean(RetryUtils.class)
            .of(
                Exponential.builder()
                    .delayFactor(2.0)
                    .interval(Duration.ofSeconds(1))
                    .maxInterval(Duration.ofSeconds(15))
                    .maxAttempt(-1)
                    .maxDuration(Duration.ofMinutes(10))
                    .build(),
                runContext.logger()
            );

        String executionRendererId = runContext.render(runContext.render("{{ execution.id }}"));

        Execution execution = retryInstance.run(
            NoSuchElementException.class,
            () -> executionRepository.findById(null, executionRendererId)
                .filter(e -> e.getState().getCurrent().isTerminated())
                .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionRendererId + "'"))
        );

        return Output.builder()
            .value(execution.toString())
            .build();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String value;
    }
}
