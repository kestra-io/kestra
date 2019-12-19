package org.floworc.core.runners;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.floworc.core.exceptions.MissingRequiredInput;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.Input;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.storages.StorageObject;
import org.floworc.core.utils.Await;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
public class RunnerUtils {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

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

                StorageObject from = storageInterface.from(flow, execution, file.getFilename(), tempFile);

                return new AbstractMap.SimpleEntry<>(
                    file.getFilename(),
                    from.getUri().toString()
                );
            })
            .toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .blockingGet();

        HashMap<String, String> merged = new HashMap<>();
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
                String current = in.get(input.getName());

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
                            URI uri = URI.create(current);

                            if (uri.getScheme() != null && uri.getScheme().equals("floworc")) {
                                return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                                    input.getName(),
                                    new StorageObject(this.storageInterface, uri)
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

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(flow, inputs, null);
    }

    public Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        if (duration == null) {
            duration = Duration.ofMinutes(5);
        }

        Execution execution = this.newExecution(flow, inputs);

        return this.awaitExecution(
            flow,
            () -> {
                this.executionQueue.emit(execution);

                return execution.getId();
            },
            duration
        );
    }

    public Execution awaitExecution(Flow flow, Supplier<String> emitExecution, Duration duration) throws TimeoutException {
        AtomicReference<String> executionId = new AtomicReference<>();
        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(current -> {
            if (current.getId().equals(executionId.get()) && current.isTerminatedWithListeners(flow)) {
                receive.set(current);
            }
        });

        executionId.set(emitExecution.get());

        Await.until(() -> receive.get() != null, null, duration);

        cancel.run();

        return receive.get();
    }

    public Execution newExecution(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs) {
        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
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
