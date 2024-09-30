package io.kestra.core.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.ItemTypeInterface;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Service class for manipulating Flow's Inputs and Outputs.
 */
@Singleton
public class FlowInputOutput {
    private static final Logger log = LoggerFactory.getLogger(FlowInputOutput.class);

    public static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)$");
    public static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml();
    private final StorageInterface storageInterface;
    private final Optional<String> secretKey;
    private final RunContextFactory runContextFactory;

    @Inject
    public FlowInputOutput(
        StorageInterface storageInterface,
        RunContextFactory runContextFactory,
        @Nullable @Value("${kestra.encryption.secret-key}") String secretKey
    ) {
        this.storageInterface = storageInterface;
        this.runContextFactory = runContextFactory;
        this.secretKey = Optional.ofNullable(secretKey);
    }

    /**
     * Validate all the inputs of a given execution of a flow.
     *
     * @param inputs                  The Flow's inputs.
     * @param execution               The Execution.
     * @param data                    The Execution's inputs data.
     * @param deleteInputsFromStorage Specifies whether inputs stored on internal storage should be deleted before returning.
     * @return The list of {@link InputAndValue}.
     */
    public List<InputAndValue> validateExecutionInputs(final List<Input<?>> inputs,
                                                       final Execution execution,
                                                       final Publisher<CompletedPart> data,
                                                       final boolean deleteInputsFromStorage) throws IOException {
        if (ListUtils.isEmpty(inputs)) return Collections.emptyList();

        Map<String, ?> dataByInputId = readData(inputs, execution, data);

        List<InputAndValue> values = this.resolveInputs(inputs, execution, dataByInputId);
        if (deleteInputsFromStorage) {
            values.stream()
                .filter(it -> it.input() instanceof FileInput && Objects.nonNull(it.value()))
                .forEach(it -> {
                    try {
                        URI uri = URI.create(it.value().toString());
                        storageInterface.delete(execution.getTenantId(), uri);
                    } catch (IllegalArgumentException | IOException e) {
                        log.debug("Failed to remove execution input after validation [{}]", it.value(), e);
                    }
                });
        }
        return values;
    }

    /**
     * Reads all the inputs of a given execution of a flow.
     *
     * @param flow      The Flow.
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> readExecutionInputs(final Flow flow,
                                                   final Execution execution,
                                                   final Publisher<CompletedPart> data) throws IOException {
        return this.readExecutionInputs(flow.getInputs(), execution, data);
    }

    /**
     * Reads all the inputs of a given execution of a flow.
     *
     * @param inputs    The Flow's inputs
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> readExecutionInputs(final List<Input<?>> inputs,
                                                   final Execution execution,
                                                   final Publisher<CompletedPart> data) throws IOException {
        return this.readExecutionInputs(inputs, execution, readData(inputs, execution, data));
    }

    private Map<String, ?> readData(List<Input<?>> inputs, Execution execution, Publisher<CompletedPart> data) throws IOException {
        return Flux.from(data)
            .subscribeOn(Schedulers.boundedElastic())
            .map(throwFunction(input -> {
                if (input instanceof CompletedFileUpload fileUpload) {
                    final String fileExtension = FileInput.findFileInputExtension(inputs, fileUpload.getFilename());
                    File tempFile = File.createTempFile(fileUpload.getFilename() + "_", fileExtension);
                    try (var inputStream = fileUpload.getInputStream();
                         var outputStream = new FileOutputStream(tempFile)) {
                        long transferredBytes = inputStream.transferTo(outputStream);
                        if (transferredBytes == 0) {
                            throw new RuntimeException("Can't upload file: " + fileUpload.getFilename());
                        }

                        URI from = storageInterface.from(execution, fileUpload.getFilename(), tempFile);
                        return new AbstractMap.SimpleEntry<>(fileUpload.getFilename(), from.toString());
                    } finally {
                        if (!tempFile.delete()) {
                            tempFile.deleteOnExit();
                        }
                    }
                } else {
                    return new AbstractMap.SimpleEntry<>(input.getName(), new String(input.getBytes()));
                }
            }))
            .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .block();
    }

    /**
     * Utility method for retrieving types inputs for a flow.
     *
     * @param flow      The Flow.
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> readExecutionInputs(
        final Flow flow,
        final Execution execution,
        final Map<String, ?> data
    ) {
       return readExecutionInputs(flow.getInputs(), execution, data);
    }

    private Map<String, Object> readExecutionInputs(
        final List<Input<?>> inputs,
        final Execution execution,
        final Map<String, ?> data
    ) {
        Map<String, Object> resolved = this.resolveInputs(inputs, execution, data)
            .stream()
            .filter(InputAndValue::enabled)
            .map(it -> {
                //TODO check to return all exception at-once.
                if (it.exception() != null) {
                    throw it.exception();
                }
                return new AbstractMap.SimpleEntry<>(it.input().getId(), it.value());
            })
            .collect(HashMap::new, (m,v)-> m.put(v.getKey(), v.getValue()), HashMap::putAll);
        return MapUtils.flattenToNestedMap(resolved);
    }

    /**
     * Utility method for retrieving types inputs.
     *
     * @param inputs    The Flow's inputs
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    @VisibleForTesting
    public List<InputAndValue> resolveInputs(
        final List<Input<?>> inputs,
        final Execution execution,
        final Map<String, ?> data
    ) {
        if (inputs == null) {
            return Collections.emptyList();
        }

        final Map<String, ResolvableInput> resolvableInputMap = Collections.unmodifiableMap(inputs.stream()
            .map(input -> {
                // get value or default
                Object value = Optional.ofNullable((Object) data.get(input.getId())).orElseGet(input::getDefaults);
                return ResolvableInput.of(input, value);
            })
            .collect(Collectors.toMap(it -> it.get().input().getId(), Function.identity(), (o1, o2) -> o1, LinkedHashMap::new)));

        resolvableInputMap.values().forEach(input -> resolveInputValue(input, execution, resolvableInputMap));

        return resolvableInputMap.values().stream().map(ResolvableInput::get).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private InputAndValue resolveInputValue(
        final @NotNull ResolvableInput resolvable,
        final @NotNull Execution execution,
        final @NotNull Map<String, ResolvableInput> inputs) {

        // return immediately if the input is already resolved
        if (resolvable.isResolved()) return resolvable.get();

        Input<?> input = resolvable.get().input();

        try {
            //  resolve all input dependencies and check whether input is enabled
            final Map<String, InputAndValue> dependencies = resolveAllDependentInputs(input, execution, inputs);
            final RunContext runContext = buildRunContextForExecutionAndInputs(execution, dependencies);

            boolean isInputEnabled = dependencies.isEmpty() || dependencies.values().stream().allMatch(InputAndValue::enabled);

            final Optional<String> dependsOnCondition = Optional.ofNullable(input.getDependsOn()).map(DependsOn::condition);
            if (dependsOnCondition.isPresent() && isInputEnabled) {
                try {
                    isInputEnabled = Boolean.TRUE.equals(runContext.renderTyped(dependsOnCondition.get()));
                } catch (IllegalVariableEvaluationException e) {
                    resolvable.resolveWithError(ManualConstraintViolation.toConstraintViolationException(
                        "Invalid condition: " + e.getMessage(),
                        input,
                        (Class<Input>)input.getClass(),
                        input.getId(),
                        this
                    ));
                    isInputEnabled = false;
                }
            }

            // return immediately if the input is not enabled
            if (!isInputEnabled) {
                resolvable.resolveWithEnabled(false);
                return resolvable.get();
            }

            // render input
            input = RenderableInput.mayRenderInput(input, expression -> {
                try {
                    return runContext.renderTyped(expression);
                } catch (IllegalVariableEvaluationException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            });
            resolvable.setInput(input);

            // validate and parse input value
            final Object value = resolvable.get().value();
            if (value == null) {
                if (input.getRequired()) {
                    resolvable.resolveWithError(input.toConstraintViolationException("missing required input", null));
                } else {
                    resolvable.resolveWithValue(null);
                }
            } else {
                var parsedInput = parseData(execution, input, value);
                try {
                    parsedInput.ifPresent(parsed -> ((Input) resolvable.get().input()).validate(parsed.getValue()));
                    parsedInput.ifPresent(typed -> resolvable.resolveWithValue(typed.getValue()));
                } catch (ConstraintViolationException e) {
                    ConstraintViolationException exception = e.getConstraintViolations().size() == 1 ?
                        input.toConstraintViolationException(List.copyOf(e.getConstraintViolations()).getFirst().getMessage(), value) :
                        input.toConstraintViolationException(e.getMessage(), value);
                    resolvable.resolveWithError(exception);
                }
            }
        } catch (ConstraintViolationException e) {
            resolvable.resolveWithError(e);
        } catch (Exception e) {
            ConstraintViolationException exception = input.toConstraintViolationException(e instanceof IllegalArgumentException ? e.getMessage() : e.toString(), resolvable.get().value());
            resolvable.resolveWithError(exception);
        }

        return resolvable.get();
    }

    private RunContext buildRunContextForExecutionAndInputs(Execution execution, Map<String, InputAndValue> dependencies) {
        Map<String, Object> flattenInputs = MapUtils.flattenToNestedMap(dependencies.entrySet()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue().value()), HashMap::putAll)
        );
        return runContextFactory.of(null, execution, vars -> vars.withInputs(flattenInputs));
    }

    private Map<String, InputAndValue> resolveAllDependentInputs(final Input<?> input, final Execution execution, final Map<String, ResolvableInput> inputs) {
        return Optional.ofNullable(input.getDependsOn())
            .map(DependsOn::inputs)
            .stream()
            .flatMap(Collection::stream)
            .filter(id -> !id.equals(input.getId()))
            .map(inputs::get)
            .filter(Objects::nonNull) // input may declare unknown or non-necessary dependencies. Let's ignore.
            .map(it -> resolveInputValue(it, execution, inputs))
            .collect(Collectors.toMap(it -> it.input().getId(), Function.identity()));
    }

    public Map<String, Object> typedOutputs(
        final Flow flow,
        final Execution execution,
        final Map<String, Object> in
    ) {
        if (flow.getOutputs() == null) {
            return ImmutableMap.of();
        }
        Map<String, Object> results = flow
            .getOutputs()
            .stream()
            .map(output -> {
                Object current = in == null ? null : in.get(output.getId());
                try {
                    return parseData(execution, output, current)
                        .map(entry -> {
                            if (output.getType().equals(Type.SECRET)) {
                                return new AbstractMap.SimpleEntry<>(
                                    entry.getKey(),
                                    EncryptedString.from(entry.getValue().toString())
                                );
                            }
                            return entry;
                        });
                } catch (Exception e) {
                    throw output.toConstraintViolationException(e.getMessage(), current);
                }
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        // Ensure outputs are compliant with tasks outputs.
        return JacksonMapper.toMap(results);
    }

    private Optional<AbstractMap.SimpleEntry<String, Object>> parseData(
        final Execution execution,
        final Data data,
        final Object current
    ) throws Exception {
        if (data.getType() == null) {
            return Optional.of(new AbstractMap.SimpleEntry<>(data.getId(), current));
        }

        final Type elementType = data instanceof ItemTypeInterface itemTypeInterface ? itemTypeInterface.getItemType() : null;

        return Optional.of(new AbstractMap.SimpleEntry<>(
            data.getId(),
            parseType(execution, data.getType(), data.getId(), elementType, current)
        ));
    }

    private Object parseType(Execution execution, Type type, String id, Type elementType, Object current) throws Exception {
        try {
            return switch (type) {
                case SELECT, ENUM, STRING -> current;
                case SECRET -> {
                    if (secretKey.isEmpty()) {
                        throw new Exception("Unable to use a `SECRET` input/output as encryption is not configured");
                    }
                    yield EncryptionService.encrypt(secretKey.get(), (String) current);
                }
                case INT -> current instanceof Integer ? current : Integer.valueOf((String) current);
                case FLOAT -> current instanceof Float ? current : Float.valueOf((String) current);
                case BOOLEAN -> current instanceof Boolean ? current : Boolean.valueOf((String) current);
                case DATETIME -> Instant.parse(((String) current));
                case DATE -> LocalDate.parse(((String) current));
                case TIME -> LocalTime.parse(((String) current));
                case DURATION -> Duration.parse(((String) current));
                case FILE -> {
                    URI uri = URI.create(((String) current).replace(File.separator, "/"));

                    if (uri.getScheme() != null && uri.getScheme().equals("kestra")) {
                        yield uri;
                    } else {
                        yield storageInterface.from(execution, id, new File(((String) current)));
                    }
                }
                case JSON -> JacksonMapper.toObject(((String) current));
                case YAML -> YAML_MAPPER.readValue((String) current, JacksonMapper.OBJECT_TYPE_REFERENCE);
                case URI -> {
                    Matcher matcher = URI_PATTERN.matcher((String) current);
                    if (matcher.matches()) {
                        yield current;
                    } else {
                        throw new IllegalArgumentException("Expected `URI` but received `" + current + "`");
                    }
                }
                case ARRAY, MULTISELECT -> {
                    List<?> asList;
                    if (current instanceof List<?> list) {
                        asList = list;
                    } else {
                        asList = JacksonMapper.toList(((String) current));
                    }

                    if (elementType != null) {
                        // recursively parse the elements only once
                        yield asList.stream()
                            .map(throwFunction(element -> {
                                try {
                                    return parseType(execution, elementType, id, null, element);
                                } catch (Throwable e) {
                                    throw new IllegalArgumentException("Unable to parse array element as `" + elementType + "` on `" + element + "`", e);
                                }
                            }))
                            .toList();
                    } else {
                        yield asList;
                    }
                }
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new Exception("Expected `" + type + "` but received `" + current + "` with errors:\n```\n" + e.getMessage() + "\n```");
        }
    }

    /**
     * Mutable wrapper to hold a flow's input, and it's resolved value.
     */
    private static class ResolvableInput implements Supplier<InputAndValue> {
        /**
         * The flow's inputs.
         */
        private InputAndValue input;
        /**
         * Specify whether the input's value is resoled.
         */
        private boolean isResolved;

        public static ResolvableInput of(@NotNull final Input<?> input, @Nullable final Object value) {
            return new ResolvableInput(new InputAndValue(input, value), false);
        }

        private ResolvableInput(InputAndValue input, boolean isResolved) {
            this.input = input;
            this.isResolved = isResolved;
        }

        @Override
        public InputAndValue get() {
            return input;
        }

        public void setInput(final Input<?> input) {
            this.input = new InputAndValue(input, this.input.value(), this.input.enabled(), this.input.exception());
        }

        public void resolveWithEnabled(boolean enabled) {
            this.input = new InputAndValue(this.input.input(), input.value(), enabled, this.input.exception());
            markAsResolved();
        }

        public void resolveWithValue(@Nullable Object value) {
            this.input = new InputAndValue(this.input.input(), value,  this.input.enabled(),  this.input.exception());
            markAsResolved();
        }

        public void resolveWithError(@Nullable ConstraintViolationException exception) {
            this.input = new InputAndValue(this.input.input(),  this.input.value(),  this.input.enabled(), exception);
            markAsResolved();
        }

        private void markAsResolved() {
            this.isResolved = true;
        }

        public boolean isResolved() {
            return isResolved;
        }
    }
}
