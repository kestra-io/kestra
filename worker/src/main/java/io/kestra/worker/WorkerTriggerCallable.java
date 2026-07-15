package io.kestra.worker;

import java.time.Duration;
import java.util.Optional;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import lombok.Getter;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerCallable extends AbstractWorkerTriggerCallable {
    PollingTriggerInterface pollingTrigger;

    // Maximum time the evaluation is allowed to run before being interrupted; null disables the timeout.
    private final Duration triggerEvaluationTimeout;

    @Getter
    Optional<Execution> evaluate;

    WorkerTriggerCallable(RunContext runContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        this(runContext, workerTrigger, pollingTrigger, null);
    }

    WorkerTriggerCallable(RunContext runContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger, Duration triggerEvaluationTimeout) {
        super(runContext, pollingTrigger.getClass().getName(), workerTrigger);
        this.pollingTrigger = pollingTrigger;
        this.triggerEvaluationTimeout = triggerEvaluationTimeout;
    }

    @Override
    public State.Type doCall() throws Exception {
        if (triggerEvaluationTimeout == null) {
            this.evaluate = this.evaluateTrigger();
            return SUCCESS;
        }

        Timeout<Object> timeout = Timeout
            .builder(triggerEvaluationTimeout)
            .withInterrupt() // use to awake blocking evaluations, e.g. a stuck KafkaConsumer.poll().
            .build();
        try {
            Failsafe.with(timeout).run(() -> this.evaluate = this.evaluateTrigger());
            return SUCCESS;
        } catch (dev.failsafe.TimeoutExceededException e) {
            // Interrupt the (potentially blocked) evaluation and free the worker thread.
            // Setting the exception (killed stays false) makes the caller emit an error trigger result,
            // which releases the scheduler's evaluation lock instead of leaving the trigger stuck.
            kill(false);
            // Clear the interrupt flag set by Failsafe's withInterrupt() so it doesn't leak
            // to the caller (e.g., queue emission after timeout).
            Thread.interrupted();
            return this.exceptionHandler(new TimeoutExceededException(triggerEvaluationTimeout));
        } catch (dev.failsafe.FailsafeException e) {
            // Failsafe wraps evaluation exceptions; unwrap so they are handled like a normal evaluation failure.
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private Optional<Execution> evaluateTrigger() throws Exception {
        return this.pollingTrigger.evaluate(
            workerTrigger.getConditionContext().withRunContext(runContext),
            workerTrigger.getTriggerContext()
        );
    }
}
