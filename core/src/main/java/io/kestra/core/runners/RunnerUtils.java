package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.crypto.CryptoService;
import io.kestra.core.exceptions.MissingRequiredInput;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ConditionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.micronaut.http.multipart.StreamingFileUpload;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class RunnerUtils {
    public static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)$");

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ConditionService conditionService;

    @Inject
    private CryptoService cryptoService;

    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, Object> in, Publisher<StreamingFileUpload> files) throws IOException {
        if (files == null) {
            return this.typedInputs(flow, execution, in);
        }

        Map<String, String> uploads = Flux.from(files)
            .subscribeOn(Schedulers.boundedElastic())
            .map(throwFunction(file -> {
                File tempFile = File.createTempFile(file.getFilename() + "_", ".upl");
                Publisher<Boolean> uploadPublisher = file.transferTo(tempFile);
                Boolean bool = Mono.from(uploadPublisher).block();

                if (!bool) {
                    throw new RuntimeException("Can't upload");
                }

                URI from = storageInterface.from(execution, file.getFilename(), tempFile);
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();

                return new AbstractMap.SimpleEntry<>(
                    file.getFilename(),
                    from.toString()
                );
            }))
            .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .block();

        Map<String, Object> merged = new HashMap<>();
        if (in != null) {
            merged.putAll(in);
        }
        merged.putAll(uploads);

        return this.typedInputs(flow, execution, merged);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> typedInputs(Flow flow, Execution execution, Map<String, Object> in) {
        if (flow.getInputs() == null) {
            return ImmutableMap.of();
        }

        Map<String, Object> results = flow
            .getInputs()
            .stream()
            .map((Function<Input, Optional<AbstractMap.SimpleEntry<String, Object>>>) input -> {
                Object current = in == null ? null : in.get(input.getId());

                if (current == null && input.getDefaults() != null) {
                    current = input.getDefaults();
                }

                if (input.getRequired() && current == null) {
                    throw new MissingRequiredInput("Missing required input value '" + input.getId() + "'");
                }

                if (!input.getRequired() && current == null) {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        null
                    ));
                }

                var parsedInput = parseInput(flow, execution, input, current);
                parsedInput.ifPresent(parsed -> input.validate(parsed.getValue()));
                return parsedInput;
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        return handleNestedInputs(results);
    }

    private Optional<AbstractMap.SimpleEntry<String, Object>> parseInput(Flow flow, Execution execution, Input<?> input, Object current) {
        switch (input.getType()) {
            case STRING -> {
                return Optional.of(new AbstractMap.SimpleEntry<>(
                    input.getId(),
                    current
                ));
            }
            case SECRET -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        cryptoService.encrypt((String) current)
                    ));
                } catch (GeneralSecurityException e) {
                    throw new MissingRequiredInput("Invalid SECRET format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case INT -> {
                return Optional.of(new AbstractMap.SimpleEntry<>(
                    input.getId(),
                    current instanceof Integer ? current : Integer.valueOf((String) current)
                ));
            }
            case FLOAT -> {
                return Optional.of(new AbstractMap.SimpleEntry<>(
                    input.getId(),
                    current instanceof Float ? current : Float.valueOf((String) current)
                ));
            }
            case BOOLEAN -> {
                return Optional.of(new AbstractMap.SimpleEntry<>(
                    input.getId(),
                    current instanceof Boolean ? current : Boolean.valueOf((String) current)
                ));
            }
            case DATETIME -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        Instant.parse(((String) current))
                    ));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredInput("Invalid DATETIME format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case DATE -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        LocalDate.parse(((String) current))
                    ));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredInput("Invalid DATE format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case TIME -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        LocalTime.parse(((String) current))
                    ));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredInput("Invalid TIME format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case DURATION -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        Duration.parse(((String) current))
                    ));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredInput("Invalid DURATION format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case FILE -> {
                try {
                    URI uri = URI.create(((String) current).replace(File.separator, "/"));

                    if (uri.getScheme() != null && uri.getScheme().equals("kestra")) {
                        return Optional.of(new AbstractMap.SimpleEntry<>(
                            input.getId(),
                            uri
                        ));
                    } else {
                        return Optional.of(new AbstractMap.SimpleEntry<>(
                            input.getId(),
                            storageInterface.from(execution, input.getId(), new File(((String) current)))
                        ));
                    }
                } catch (Exception e) {
                    throw new MissingRequiredInput("Invalid input arguments for file on input '" + input.getId() + "'", e);
                }
            }
            case JSON -> {
                try {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        JacksonMapper.toObject(((String) current))
                    ));
                } catch (JsonProcessingException e) {
                    throw new MissingRequiredInput("Invalid JSON format for '" + input.getId() + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case URI -> {
                Matcher matcher = URI_PATTERN.matcher((String) current);
                if (matcher.matches()) {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        current
                    ));
                } else {
                    throw new MissingRequiredInput("Invalid URI format for '" + input.getId() + "' for '" + current + "'");
                }
            }
            default ->
                throw new MissingRequiredInput("Invalid input type '" + input.getType() + "' for '" + input.getId() + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleNestedInputs(Map<String, Object> inputs) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String[] f = entry.getKey().split("\\.");
            Map<String, Object> t = result;
            int i = 0;
            for (int m = f.length - 1; i < m; ++i) {
                t = (Map<String, Object>) t.computeIfAbsent(f[i], k -> new TreeMap<>());
            }

            t.put(f[i], entry.getValue());
        }

        return result;
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

        Execution execution = this.newExecution(flow, inputs, labels);

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
            duration = Duration.ofSeconds(15);
        }

        Execution execution = this.newExecution(flow, inputs, null);

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
            duration = Duration.ofSeconds(15);
        }

        Execution execution = this.newExecution(flow, inputs, null);

        return this.awaitExecution(isRunningExecution(execution), () -> {
            this.executionQueue.emit(execution);
        }, duration);
    }

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

    public Execution awaitChildExecution(Flow flow, Execution parentExecution, Runnable executionEmitter, Duration duration) throws TimeoutException {
        return this.awaitExecution(isTerminatedChildExecution(parentExecution, flow), executionEmitter, duration);
    }

    public Execution awaitExecution(Flow flow, Execution execution, Runnable executionEmitter, Duration duration) throws TimeoutException {
        return this.awaitExecution(isTerminatedExecution(execution, flow), executionEmitter, duration);
    }

    public Predicate<Execution> isTerminatedExecution(Execution execution, Flow flow) {
        return e -> e.getId().equals(execution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }

    public Predicate<Execution> isPausedExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isPaused() && e.getTaskRunList().stream().anyMatch(t -> t.getState().isPaused());
    }

    public Predicate<Execution> isRunningExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isRunning() && e.getTaskRunList().stream().anyMatch(t -> t.getState().isRunning());
    }

    private Predicate<Execution> isTerminatedChildExecution(Execution parentExecution, Flow flow) {
        return e -> e.getParentId() != null && e.getParentId().equals(parentExecution.getId()) && conditionService.isTerminatedWithListeners(flow, e);
    }

    public Execution newExecution(Flow flow, BiFunction<Flow, Execution, Map<String, Object>> inputs, List<Label> labels) {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        List<Label> executionLabels = new ArrayList<>();
        if (flow.getLabels() != null) {
            executionLabels.addAll(flow.getLabels());
        }
        if (labels != null) {
            executionLabels.addAll(labels);
        }
        if (!executionLabels.isEmpty()) {
            execution = execution.withLabels(executionLabels);
        }

        return execution;
    }

}
