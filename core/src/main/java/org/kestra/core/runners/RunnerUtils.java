package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.kestra.core.exceptions.MissingRequiredInput;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.services.ConditionService;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.utils.Await;
import org.kestra.core.utils.IdUtils;
import org.reactivestreams.Publisher;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class RunnerUtils {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ConditionService conditionService;

    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, String> in, Publisher<StreamingFileUpload> files) {
        if (files == null) {
            return this.typedInputs(flow, execution, in);
        }

        Map<String, String> uploads = Flowable.fromPublisher(files)
            .subscribeOn(Schedulers.io())
            .map(file -> {
                File tempFile = File.createTempFile(file.getFilename() + "_", ".upl");
                Publisher<Boolean> uploadPublisher = file.transferTo(tempFile);
                Boolean bool = Single.fromPublisher(uploadPublisher).blockingGet();

                if (!bool) {
                    throw new RuntimeException("Can't upload");
                }

                URI from = storageInterface.from(flow, execution, file.getFilename(), tempFile);
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();

                return new AbstractMap.SimpleEntry<>(
                    file.getFilename(),
                    from.toString()
                );
            })
            .toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .blockingGet();

        Map<String, String> merged = new HashMap<>();
        if (in != null) {
            merged.putAll(in);
        }
        merged.putAll(uploads);

        return this.typedInputs(flow, execution, merged);
    }

    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, String> in) {
        if (flow.getInputs() == null) {
            return ImmutableMap.of();
        }

        return flow
            .getInputs()
            .stream()
            .map((Function<Input, Optional<AbstractMap.SimpleEntry<String, Object>>>) input -> {
                String current = in == null ? null : in.get(input.getName());

                if (input.getRequired() && current == null) {
                    throw new MissingRequiredInput("Missing required input value '" + input.getName() + "'");
                }

                if (!input.getRequired() && current == null) {
                    return Optional.empty();
                }

                switch (input.getType()) {
                    case STRING:
                        return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                            input.getName(),
                            current
                        ));

                    case INT:
                        return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                            input.getName(),
                            Integer.valueOf(current)
                        ));

                    case FLOAT:
                        return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                            input.getName(),
                            Float.valueOf(current)
                        ));

                    case DATETIME:
                        return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                            input.getName(),
                            Instant.parse(current)
                        ));

                    case FILE:
                        try {
                            URI uri = URI.create(current.replace(File.separator, "/"));

                            if (uri.getScheme() != null && uri.getScheme().equals("kestra")) {
                                return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                                    input.getName(),
                                    uri
                                ));
                            } else {
                                return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                                    input.getName(),
                                    storageInterface.from(flow, execution, input, new File(current))
                                ));
                            }
                        } catch (Exception e) {
                            throw new MissingRequiredInput("Invalid input for '" + input.getName() + "'", e);
                        }

                    default:
                        throw new MissingRequiredInput("Invalid input type '" + input.getType() + "' for '" + input.getName() + "'");
                }
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Execution runOne(String namespace, String flowId) throws TimeoutException {
        return this.runOne(namespace, flowId, null, null, null);
    }

    public Execution runOne(String namespace, String flowId, Integer revision) throws TimeoutException {
        return this.runOne(namespace, flowId, revision, null, null);
    }

    public Execution runOne(String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(namespace, flowId, revision, inputs, null);
    }

    public Execution runOne(String namespace, String flowId, Integer revision, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOne(
            flowRepository
                .findById(namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    public Execution runOne(String namespace, String flowId, Duration duration) throws TimeoutException {
        return this.runOne(
            flowRepository
                .findById(namespace, flowId, Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            (f, e) -> ImmutableMap.of(),
            duration
        );
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(flow, inputs, null);
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        if (duration == null) {
            duration = Duration.ofSeconds(15);
        }

        Execution execution = this.newExecution(flow, inputs);

        return this.awaitExecution(isTerminatedExecution(execution, flow), () -> {
            this.executionQueue.emit(execution);
        }, duration);
    }

    public Execution awaitExecution(Predicate<Execution> predicate, Runnable executionEmitter, Duration duration) throws TimeoutException {
        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(current -> {
            if (predicate.test(current)) {
                receive.set(current);
            }
        });

        executionEmitter.run();

        if (duration == null) {
            Await.until(() -> receive.get() != null);
        } else {
            Await.until(() -> receive.get() != null, null, duration);
        }

        cancel.run();

        return receive.get();
    }

    public Execution awaitChildExecution(Flow flow, Execution parentExecution, Runnable executionEmitter, Duration duration) throws TimeoutException {
        return this.awaitExecution(isTerminatedChildExecution(parentExecution, flow), executionEmitter, duration);
    }

    public Execution awaitExecution(Flow flow, Execution execution, Runnable executionEmitter, Duration duration) throws TimeoutException {
        return this.awaitExecution(isTerminatedExecution(execution, flow), executionEmitter, duration);
    }

    public Predicate<Execution> isTerminatedExecution(Execution execution, Flow flow) {
        return e -> e.getId().equals(execution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }

    private Predicate<Execution> isTerminatedChildExecution(Execution parentExecution, Flow flow) {
        return e -> e.getParentId().equals(parentExecution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }

    public Execution newExecution(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs) {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        return execution;
    }

}
