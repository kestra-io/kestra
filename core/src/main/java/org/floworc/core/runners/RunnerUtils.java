package org.floworc.core.runners;

import com.devskiller.friendly_id.FriendlyId;
import org.floworc.core.exceptions.MissingRequiredInput;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.Input;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.utils.Await;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    public Execution runOne(String flowId) throws TimeoutException {
        return this.runOne(flowId, null, null);
    }

    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, String> in) {
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
                            return Optional.of(new AbstractMap.SimpleEntry<String, Object>(
                                input.getName(),
                                storageInterface.from(flow, execution, input, new File(current))
                            ));
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

    public Execution runOne(String flowId, BiFunction<Flow, Execution, Map<String, Object>> inputs) throws TimeoutException {
        return this.runOne(flowId, inputs, null);
    }

    public Execution runOne(String flowId, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        return this.runOne(
            flowRepository
                .findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    private Execution runOne(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, Duration duration) throws TimeoutException {
        if (duration == null) {
            duration = Duration.ofSeconds(5);
        }

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .flowId(flow.getId())
            .state(new State())
            .build();

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        final String executionId = execution.getId();

        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(current -> {
            if (current.getId().equals(executionId) && current.getState().isTerninated()) {
                receive.set(current);
            }
        });

        this.executionQueue.emit(execution);

        Await.until(() -> receive.get() != null, null, duration);

        cancel.run();

        return receive.get();
    }

}
