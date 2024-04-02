package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.Await;
import io.micronaut.http.multipart.StreamingFileUpload;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@Singleton
public class RunnerUtils {
    public static final Duration DEFAULT_MAX_WAIT_DURATION = Duration.ofSeconds(15);

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ConditionService conditionService;

    @Inject
    private FlowInputOutput flowIO;

    @Deprecated
    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, Object> in, Publisher<StreamingFileUpload> files) throws IOException {
        return flowIO.typedInputs(flow, execution, in, files);
    }

    @Deprecated
    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, Object> in) {
        return flowIO.typedInputs(flow, execution, in);
    }

    public Execution runOne(String tenantId, String namespace, String flowId) throws TimeoutException {
        return this.runOne(tenantId, namespace, flowId, null, null, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision) throws TimeoutException {
        return this.runOne(tenantId, namespace, flowId, revision, null, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(tenantId, namespace, flowId, revision, inputs, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Duration duration) throws TimeoutException {
        return this.runOne(tenantId, namespace, flowId, null, null, duration, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOne(tenantId, namespace, flowId, revision, inputs, duration, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration, List<Label> labels) throws TimeoutException {
        return this.runOne(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration,
            labels);
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(flow, inputs, null, null);
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOne(flow, inputs, duration, null);
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration, List<Label> labels) throws TimeoutException {
        if (duration == null) {
            duration = Duration.ofSeconds(15);
        }

        Execution execution = Execution.newExecution(flow, inputs, labels);

        return this.awaitExecution(isTerminatedExecution(execution, flow), () -> {
            this.executionQueue.emit(execution);
        }, duration);
    }

    public Execution runOneUntilPaused(String tenantId, String namespace, String flowId) throws TimeoutException {
        return this.runOneUntilPaused(tenantId, namespace, flowId, null, null, null);
    }

    public Execution runOneUntilPaused(String tenantId, String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOneUntilPaused(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    public Execution runOneUntilPaused(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        if (duration == null) {
            duration = DEFAULT_MAX_WAIT_DURATION;
        }

        Execution execution = Execution.newExecution(flow, inputs, null);

        return this.awaitExecution(isPausedExecution(execution), () -> {
            this.executionQueue.emit(execution);
        }, duration);
    }

    public Execution runOneUntilRunning(String tenantId, String namespace, String flowId) throws TimeoutException {
        return this.runOneUntilRunning(tenantId, namespace, flowId, null, null, null);
    }

    public Execution runOneUntilRunning(String tenantId, String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOneUntilRunning(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    public Execution runOneUntilRunning(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        if (duration == null) {
            duration = DEFAULT_MAX_WAIT_DURATION;
        }

        Execution execution = Execution.newExecution(flow, inputs, null);

        return this.awaitExecution(isRunningExecution(execution), () -> {
            this.executionQueue.emit(execution);
        }, duration);
    }

    @VisibleForTesting
    public Execution awaitExecution(Predicate<Execution> predicate, Runnable executionEmitter, Duration duration) throws TimeoutException {
        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(current -> {
            if (predicate.test(current.getLeft())) {
                receive.set(current.getLeft());
            }
        });

        executionEmitter.run();

        if (duration == null) {
            Await.until(() -> receive.get() != null, Duration.ofMillis(10));
        } else {
            Await.until(() -> receive.get() != null, Duration.ofMillis(10), duration);
        }

        cancel.run();

        return receive.get();
    }

    @VisibleForTesting
    public Execution awaitChildExecution(Flow flow, Execution parentExecution, Runnable executionEmitter, Duration duration) throws TimeoutException {
        return this.awaitExecution(isTerminatedChildExecution(parentExecution, flow), executionEmitter, duration);
    }

    private Predicate<Execution> isTerminatedExecution(Execution execution, Flow flow) {
        return e -> e.getId().equals(execution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }

    private Predicate<Execution> isPausedExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isPaused() && e.getTaskRunList() != null && e.getTaskRunList().stream().anyMatch(t -> t.getState().isPaused());
    }

    private Predicate<Execution> isRunningExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isRunning() && e.getTaskRunList() != null && e.getTaskRunList().stream().anyMatch(t -> t.getState().isRunning());
    }

    private Predicate<Execution> isTerminatedChildExecution(Execution parentExecution, Flow flow) {
        return e -> e.getParentId() != null && e.getParentId().equals(parentExecution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }
}
