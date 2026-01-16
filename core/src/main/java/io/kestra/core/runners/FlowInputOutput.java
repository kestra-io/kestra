package io.kestra.core.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;

import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.ItemTypeInterface;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.property.URIFetcher;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
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
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
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
    private static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)$");
    private static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml();

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
     * @param inputs    The Flow's inputs.
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The list of {@link InputAndValue}.
     */
    public Mono<List<InputAndValue>> validateExecutionInputs(final List<Input<?>> inputs,
                                                             final FlowInterface flow,
                                                             final Execution execution,
                                                             final Publisher<CompletedPart> data) {
        if (ListUtils.isEmpty(inputs)) return Mono.just(Collections.emptyList());

        return readData(inputs, execution, data, false)
            .map(inputData -> resolveInputs(inputs, flow, execution, inputData, false));
    }

    /**
     * Reads all the inputs of a given execution of a flow.
     *
     * @param flow      The Flow.
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    public Mono<Map<String, Object>> readExecutionInputs(final FlowInterface flow,
                                                         final Execution execution,
                                                         final Publisher<CompletedPart> data) {
        return this.readExecutionInputs(flow.getInputs(), flow, execution, data);
    }

    /**
     * Reads all the inputs of a given execution of a flow.
     *
     * @param inputs    The Flow's inputs
     * @param execution The Execution.
     * @param data      The Execution's inputs data.
     * @return The Map of typed inputs.
     */
    public Mono<Map<String, Object>> readExecutionInputs(final List<Input<?>> inputs,
                                                         final FlowInterface flow,
                                                         final Execution execution,
                                                         final Publisher<CompletedPart> data) {
        return readData(inputs, execution, data, true).map(inputData -> this.readExecutionInputs(inputs, flow, execution, inputData));
    }

    private Mono<Map<String, Object>> readData(List<Input<?>> inputs, Execution execution, Publisher<CompletedPart> data, boolean uploadFiles) {
        return Flux.from(data)
            .publishOn(Schedulers.boundedElastic())
            .<Map.Entry<String, String>>handle((input, sink) -> {
                if (input instanceof CompletedFileUpload fileUpload) {
                    boolean oldStyleInput = false;
                    if ("files".equals(fileUpload.getName())) {
                        // we are maybe in an old-style usage of the input, let's check if there is an input named after the filename
                        oldStyleInput = inputs.stream().anyMatch(i -> i.getId().equals(fileUpload.getFilename()));
                    }
                    if (oldStyleInput) {
                        var runContext = runContextFactory.of(null, execution);
                        runContext.logger().warn("Using a deprecated way to upload a FILE input. You must set the input 'id' as part name and set the name of the file using the regular 'filename' part attribute.");
                    }
                    String inputId = oldStyleInput ? fileUpload.getFilename() : fileUpload.getName();
                    String fileName = oldStyleInput ? FileInput.findFileInputExtension(inputs, fileUpload.getFilename()) : fileUpload.getFilename();

                    if (!uploadFiles) {
                        URI from = URI.create("kestra://" + StorageContext
                            .forInput(execution, inputId, fileName)
                            .getContextStorageURI()
                        );
                        fileUpload.discard();
                        sink.next(Map.entry(inputId, from.toString()));
                    } else {
                        try {
                            final String fileExtension = FileInput.findFileInputExtension(inputs, fileName);

                            String prefix = StringUtils.leftPad(fileName + "_", 3, "_");
                            File tempFile = File.createTempFile(prefix, fileExtension);
                            try (var inputStream = fileUpload.getInputStream();
                                 var outputStream = new FileOutputStream(tempFile)) {
                                inputStream.transferTo(outputStream);
                                URI from = storageInterface.from(execution, inputId, fileName, tempFile);
                                sink.next(Map.entry(inputId, from.toString()));
                            } finally {
                                if (!tempFile.delete()) {
                                    tempFile.deleteOnExit();
                                }
                            }
                        } catch (IOException e) {
                            fileUpload.discard();
                            sink.error(e);
                        }
                    }
                } else {
                    try {
                        sink.next(Map.entry(input.getName(), new String(input.getBytes())));
                    } catch (IOException e) {
                        sink.error(e);
                    }
                }
            })
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
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
        final FlowInterface flow,
        final Execution execution,
        final Map<String, ?> data
    ) {
       return readExecutionInputs(flow.getInputs(), flow, execution, data);
    }

    private Map<String, Object> readExecutionInputs(
        final List<Input<?>> inputs,
        final FlowInterface flow,
        final Execution execution,
        final Map<String, ?> data
    ) {
        Map<String, Object> resolved = this.resolveInputs(inputs, flow, execution, data, true)
            .stream()
            .filter(InputAndValue::enabled)
            .map(it -> {
                //TODO check to return all exception at-once.
                if (it.exceptions() != null && !it.exceptions().isEmpty()) {
                    throw  InputOutputValidationException.merge(it.exceptions());
                }
                return new AbstractMap.SimpleEntry<>(it.input().getId(), it.value());
            })
            .collect(HashMap::new, (m,v)-> m.put(v.getKey(), v.getValue()), HashMap::putAll);
        if (resolved.size() < data.size()) {
            RunContext runContext = runContextFactory.of(flow, execution);
            for (var inputKey : data.keySet()) {
                if (!resolved.containsKey(inputKey)) {
                    runContext.logger().warn(
                        "Input {} was provided for workflow {}.{} but isn't declared in the workflow inputs",
                        inputKey,
                        flow.getNamespace(),
                        flow.getId()
                    );
                }
            }
        }
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
    public List<InputAndValue> resolveInputs(
        final List<Input<?>> inputs,
        final FlowInterface flow,
        final Execution execution,
        final Map<String, ?> data
    ) {
        return resolveInputs(inputs, flow, execution, data, true);
    }

    public List<InputAndValue> resolveInputs(
        final List<Input<?>> inputs,
        final FlowInterface flow,
        final Execution execution,
        final Map<String, ?> data,
        final boolean decryptSecrets
    ) {
        if (inputs == null) {
            return Collections.emptyList();
        }

        final Map<String, ResolvableInput> resolvableInputMap = Collections.unmodifiableMap(inputs.stream()
            .map(input -> ResolvableInput.of(input,data.get(input.getId())))
            .collect(Collectors.toMap(it -> it.get().input().getId(), Function.identity(), (o1, o2) -> o1, LinkedHashMap::new)));

        resolvableInputMap.values().forEach(input -> resolveInputValue(input, flow, execution, resolvableInputMap, decryptSecrets));

        return resolvableInputMap.values().stream().map(ResolvableInput::get).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private InputAndValue resolveInputValue(
        final @NotNull ResolvableInput resolvable,
        final FlowInterface flow,
        final @NotNull Execution execution,
        final @NotNull Map<String, ResolvableInput> inputs,
        final boolean decryptSecrets) {

        // return immediately if the input is already resolved
        if (resolvable.isResolved()) return resolvable.get();

        Input<?> input = resolvable.get().input();

        try {
            // Resolve all input dependencies and check whether input is enabled
            // Note: Secrets are always decrypted here because they can be part of expressions used to render inputs such as SELECT & MULTI_SELECT.
            final Map<String, InputAndValue> dependencies = resolveAllDependentInputs(input, flow, execution, inputs, true);
            final RunContext runContext = buildRunContextForExecutionAndInputs(flow, execution, dependencies, true);

            boolean isInputEnabled = dependencies.isEmpty() || dependencies.values().stream().allMatch(InputAndValue::enabled);

            final Optional<String> dependsOnCondition = Optional.ofNullable(input.getDependsOn()).map(DependsOn::condition);
            if (dependsOnCondition.isPresent() && isInputEnabled) {
                try {
                    isInputEnabled = Boolean.TRUE.equals(runContext.renderTyped(dependsOnCondition.get()));
                } catch (IllegalVariableEvaluationException e) {
                    resolvable.resolveWithError(
                        InputOutputValidationException.of("Invalid condition: " + e.getMessage())
                    );
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

            Object value = resolvable.get().value();

            // resolve default if needed
            if (value == null && input.getDefaults() != null) {
                RunContext runContextForDefault = decryptSecrets ? runContext : buildRunContextForExecutionAndInputs(flow, execution, dependencies, false);
                value = resolveDefaultValue(input, runContextForDefault);
                resolvable.isDefault(true);
            }

            // validate and parse input value
            if (value == null) {
                if (input.getRequired()) {
                    resolvable.resolveWithError(InputOutputValidationException.of("Missing required input:"  + input.getId()));
                } else {
                    resolvable.resolveWithValue(null);
                }
            } else {
                var parsedInput = parseData(execution, input, value);
                try {
                    parsedInput.ifPresent(parsed -> ((Input) resolvable.get().input()).validate(parsed.getValue()));
                    parsedInput.ifPresent(typed -> resolvable.resolveWithValue(typed.getValue()));
                } catch (ConstraintViolationException e) {
                    Input<?> finalInput = input;
                  Set<InputOutputValidationException> exceptions =  e.getConstraintViolations().stream()
                      .map(c-> InputOutputValidationException.of(c.getMessage(), finalInput))
                      .collect(Collectors.toSet());
                    resolvable.resolveWithError(exceptions);
                }
            }
        } catch (IllegalArgumentException e){
            resolvable.resolveWithError(InputOutputValidationException.of(e.getMessage(), input));
        }
        catch (Exception e) {
            resolvable.resolveWithError(InputOutputValidationException.of(e.getMessage()));
        }

        return resolvable.get();
    }

    public static Object resolveDefaultValue(Input<?> input, PropertyContext renderer) throws IllegalVariableEvaluationException {
        return switch (input.getType()) {
            case STRING, ENUM, SELECT, SECRET, EMAIL -> resolveDefaultPropertyAs(input, renderer, String.class);
            case INT -> resolveDefaultPropertyAs(input, renderer, Integer.class);
            case FLOAT -> resolveDefaultPropertyAs(input, renderer, Float.class);
            case BOOLEAN, BOOL -> resolveDefaultPropertyAs(input, renderer, Boolean.class);
            case DATETIME -> resolveDefaultPropertyAs(input, renderer, Instant.class);
            case DATE -> resolveDefaultPropertyAs(input, renderer, LocalDate.class);
            case TIME -> resolveDefaultPropertyAs(input, renderer, LocalTime.class);
            case DURATION -> resolveDefaultPropertyAs(input, renderer, Duration.class);
            case FILE, URI -> resolveDefaultPropertyAs(input, renderer, URI.class);
            case JSON, YAML -> resolveDefaultPropertyAs(input, renderer, Object.class);
            case ARRAY -> resolveDefaultPropertyAsList(input, renderer, Object.class);
            case MULTISELECT -> resolveDefaultPropertyAsList(input, renderer, String.class);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Object resolveDefaultPropertyAs(Input<?> input, PropertyContext renderer, Class<T> clazz) throws IllegalVariableEvaluationException {
        return Property.as((Property<T>) input.getDefaults().skipCache(), renderer, clazz);
    }
    @SuppressWarnings("unchecked")
    private static <T> Object resolveDefaultPropertyAsList(Input<?> input, PropertyContext renderer, Class<T> clazz) throws IllegalVariableEvaluationException {
        return Property.asList((Property<List<T>>) input.getDefaults().skipCache(), renderer, clazz);
    }

    private RunContext buildRunContextForExecutionAndInputs(final FlowInterface flow, final Execution execution, Map<String, InputAndValue> dependencies, final boolean decryptSecrets) {
        Map<String, Object> flattenInputs = MapUtils.flattenToNestedMap(dependencies.entrySet()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue().value()), HashMap::putAll)
        );
        // Hack: Pre-inject all inputs that have a default value with 'null' to prevent
        // RunContextFactory from attempting to render them when absent, which could
        // otherwise cause an exception if a Pebble expression is involved.
        List<Input<?>> inputs = Optional.ofNullable(flow).map(FlowInterface::getInputs).orElse(List.of());
        for (Input<?> input : inputs) {
            if (input.getDefaults() != null && !flattenInputs.containsKey(input.getId())) {
                flattenInputs.put(input.getId(), null);
            }
        }
        return runContextFactory.of(flow, execution, vars -> vars.withInputs(flattenInputs), decryptSecrets);
    }

    private Map<String, InputAndValue> resolveAllDependentInputs(final Input<?> input, final FlowInterface flow, final Execution execution, final Map<String, ResolvableInput> inputs, final boolean decryptSecrets) {
        return Optional.ofNullable(input.getDependsOn())
            .map(DependsOn::inputs)
            .stream()
            .flatMap(Collection::stream)
            .filter(id -> !id.equals(input.getId()))
            .map(inputs::get)
            .filter(Objects::nonNull) // input may declare unknown or non-necessary dependencies. Let's ignore.
            .map(it -> resolveInputValue(it, flow, execution, inputs, decryptSecrets))
            .collect(Collectors.toMap(it -> it.input().getId(), Function.identity()));
    }

    public Map<String, Object> typedOutputs(
        final FlowInterface flow,
        final Execution execution,
        final Map<String, Object> in
    ) {
        if (flow.getOutputs() == null) {
            return Map.of();
        }
        Map<String, Object> results = flow
            .getOutputs()
            .stream()
            .map(output -> {
                Object current = in == null ? null : in.get(output.getId());
                try {
                    if (current == null && Boolean.FALSE.equals(output.getRequired())) {
                        return Optional.of(new AbstractMap.SimpleEntry<>(output.getId(), null));
                    }
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
                }
                catch (IllegalArgumentException e){
                    throw InputOutputValidationException.of(e.getMessage(), output);
                }
                catch (Exception e) {
                    throw InputOutputValidationException.of(e.getMessage());
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
                case SELECT, ENUM, STRING, EMAIL -> current.toString();
                case SECRET -> {
                    if (secretKey.isEmpty()) {
                        throw new Exception("Unable to use a `SECRET` input/output as encryption is not configured");
                    }
                    yield EncryptionService.encrypt(secretKey.get(), current.toString());
                }
                case INT -> current instanceof Integer ? current : Integer.valueOf(current.toString());
                // Assuming that after the render we must have a double/int, so we can safely use its toString representation
                case FLOAT -> current instanceof Float ? current : Float.valueOf(current.toString());
                case BOOLEAN -> current instanceof Boolean ? current : Boolean.valueOf(current.toString());
                case BOOL -> current instanceof Boolean ? current : Boolean.valueOf(current.toString());
                case DATETIME -> current instanceof Instant ? current : Instant.parse(current.toString());
                case DATE -> current instanceof LocalDate ? current : LocalDate.parse(current.toString());
                case TIME -> current instanceof LocalTime ? current : LocalTime.parse(current.toString());
                case DURATION -> current instanceof Duration ? current : Duration.parse(current.toString());
                case FILE -> {
                    URI uri = URI.create(current.toString().replace(File.separator, "/"));

                    if (URIFetcher.supports(uri)) {
                        yield uri;
                    } else {
                        yield storageInterface.from(execution, id, current.toString().substring(current.toString().lastIndexOf("/") + 1), new File(current.toString()));
                    }
                }
                case JSON -> (current instanceof Map || current instanceof Collection<?>) ? current :  JacksonMapper.toObject(current.toString());
                case YAML -> (current instanceof Map || current instanceof Collection<?>) ? current : YAML_MAPPER.readValue(current.toString(), JacksonMapper.OBJECT_TYPE_REFERENCE);
                case URI -> {
                    Matcher matcher = URI_PATTERN.matcher(current.toString());
                    if (matcher.matches()) {
                        yield current.toString();
                    } else {
                        throw new IllegalArgumentException("Invalid URI format.");
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
            throw new Exception(" errors:\n```\n" + e.getMessage() + "\n```");
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

        public void isDefault(boolean isDefault) {
            this.input = new InputAndValue(this.input.input(), this.input.value(), this.input.enabled(), isDefault, this.input.exceptions());
        }

        public void setInput(final Input<?> input) {
            this.input = new InputAndValue(input, this.input.value(), this.input.enabled(), this.input.isDefault(), this.input.exceptions());
        }

        public void resolveWithEnabled(boolean enabled) {
            this.input = new InputAndValue(this.input.input(), input.value(), enabled, this.input.isDefault(), this.input.exceptions());
            markAsResolved();
        }

        public void resolveWithValue(@Nullable Object value) {
            this.input = new InputAndValue(this.input.input(), value,  this.input.enabled(), this.input.isDefault(), this.input.exceptions());
            markAsResolved();
        }

        public void resolveWithError(@Nullable Set<InputOutputValidationException> exception) {
            this.input = new InputAndValue(this.input.input(),  this.input.value(), this.input.enabled(), this.input.isDefault(), exception);
            markAsResolved();
        }
        private void resolveWithError(@Nullable InputOutputValidationException exception){
            resolveWithError(Collections.singleton(exception));
        }

        private void markAsResolved() {
            this.isResolved = true;
        }

        public boolean isResolved() {
            return isResolved;
        }
    }
}
